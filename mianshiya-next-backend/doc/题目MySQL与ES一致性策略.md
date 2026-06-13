# 题目 MySQL 与 ES 一致性策略

> 基于源码分析，非 README 摘抄。当前代码默认状态：ES 同步 Job 全部关闭，`@Document` 注解已注释，CRUD 层的同步调用存在但以 best-effort 方式运行。

## 1. 整体架构

MySQL 是唯一数据源（Source of Truth），ES 作为搜索加速层。一致性模型为**尽力而为的最终一致**：

- 写操作（增/改/删）在 Controller 层同步调用 ES 写入，失败时 catch 异常并记录 warn 日志，不阻塞 MySQL 事务。
- 读操作（搜索）优先走 ES，ES 不可用时降级到 MySQL。
- 后台有全量/增量两个同步 Job 负责兜底补偿，但**默认均未启用**。

## 2. CRUD 实时同步

所有同步逻辑由 `QuestionController` 在 MySQL 操作成功后触发，调用 `QuestionServiceImpl` 的同步方法。

### 2.1 新增题目

**路径**：`QuestionController.addQuestion()` (QuestionController.java:67-91)

```
1. questionService.save(question)          // 写 MySQL
2. questionService.syncQuestionToEs(question)  // 同步到 ES
```

### 2.2 更新题目

**路径**：`QuestionController.updateQuestion()` (QuestionController.java:129-157) 和 `editQuestion()` (QuestionController.java:337-370)

```
1. questionService.updateById(question)              // 写 MySQL（可能是部分字段）
2. fullQuestion = questionService.getById(id)         // 重新查出完整实体
3. questionService.syncQuestionToEs(fullQuestion)      // 以完整实体同步到 ES
```

注意步骤 2 的 re-fetch：因为更新请求可能只携带部分字段，直接用请求对象同步会导致 ES 文档字段缺失，所以必须重新从 MySQL 读取完整实体。

### 2.3 单条删除

**路径**：`QuestionController.deleteQuestion()` (QuestionController.java:100-121)

```
1. questionService.removeById(id)                    // MySQL 软删除（isDelete=1）
2. questionService.cleanupAfterQuestionDelete(id)    // 清理关联 + ES 硬删除
```

`cleanupAfterQuestionDelete` 做两件事（QuestionServiceImpl.java:467-479）：
- 删除 `question_bank_question` 关联表中该题目的所有记录
- 调用 `questionEsDao.deleteById(questionId)` **物理删除** ES 文档

### 2.4 批量删除

**路径**：`QuestionController.batchDeleteQuestions()` → `QuestionServiceImpl.batchDeleteQuestions()` (QuestionServiceImpl.java:358-380)

```
1. MySQL 批量软删除
2. 构造 isDelete=1 的 QuestionEsDTO 列表
3. questionEsDao.saveAll(esDTOList)   // ES 逻辑删除（更新 isDelete 字段）
```

**注意**：这里与单条删除的行为不一致——单条删除是 ES 物理删除，批量删除是 ES 逻辑删除。两种方式对搜索结果无影响（`searchFromEs` 始终过滤 `isDelete=0`），但对 ES 存储空间有影响。

### 2.5 AI 批量生成题目

**路径**：`QuestionServiceImpl.aiGenerateQuestions()` (QuestionServiceImpl.java:390-432)

```
1. this.saveBatch(questionList)   // 批量写 MySQL
2. （无 ES 同步调用）
```

**已知缺陷**：AI 生成的题目不会实时同步到 ES，只能依赖增量同步 Job（而该 Job 默认关闭）。在当前默认配置下，AI 生成的题目无法通过 ES 搜索到。

### 2.6 syncQuestionToEs 实现

**位置**：QuestionServiceImpl.java:457-465

```java
public void syncQuestionToEs(Question question) {
    try {
        QuestionEsDTO questionEsDTO = QuestionEsDTO.objToDto(question);
        questionEsDao.save(questionEsDTO);
    } catch (Exception e) {
        log.warn("同步题目 {} 到 ES 失败: {}", question.getId(), e.getMessage());
    }
}
```

关键设计：
- 整个操作包裹在 try-catch 中，任何异常（连接拒绝、超时、索引不存在等）都被吞掉
- 只记录 warn 级别日志，不抛出异常
- 这意味着 ES 宕机时，MySQL 写操作完全不受影响

## 3. 软删除机制

### 3.1 MySQL 侧

`Question` 实体的 `isDelete` 字段标注了 `@TableLogic`（Question.java:64-66），配合 `application.yml` 中的 MyBatis-Plus 全局配置：

```yaml
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: isDelete
      logic-delete-value: 1
      logic-not-delete-value: 0
```

所有 MyBatis-Plus 标准操作（`removeById`、`selectList` 等）自动处理软删除：
- `removeById(id)` 实际执行 `UPDATE question SET isDelete=1 WHERE id=?`
- 所有查询自动追加 `WHERE isDelete=0`

### 3.2 ES 侧

ES 文档中保留了 `isDelete` 字段（QuestionEsDTO 中定义）。`searchFromEs` 方法（QuestionServiceImpl.java:279）在查询中始终添加过滤条件：

```java
boolQueryBuilder.filter(QueryBuilders.termQuery("isDelete", 0));
```

### 3.3 同步 Job 与软删除

全量同步和增量同步 Job 使用自定义 SQL（绕过 `@TableLogic`），会查出 `isDelete=1` 的记录并同步到 ES。这确保了 ES 中 `isDelete` 字段与 MySQL 保持一致，使搜索过滤正确生效。

- `questionMapper.listAllQuestionWithDelete()`：`SELECT * FROM question`（无 WHERE 条件）
- `questionMapper.listQuestionWithDelete(minUpdateTime)`：`SELECT * FROM question WHERE updateTime >= #{minUpdateTime}`

## 4. 全量同步 Job（FullSyncQuestionToEs）

**文件**：`job/once/FullSyncQuestionToEs.java`

**当前状态**：`@Component` 已注释（第 23-24 行），默认不启用。

```java
// todo 取消注释开启任务
//@Component
```

### 4.1 运行方式

实现 `CommandLineRunner` 接口，启用后在应用启动时执行一次。

### 4.2 执行流程

1. 调用 `questionMapper.listAllQuestionWithDelete()` 全表扫描（包含软删除记录）
2. 将 `Question` 转换为 `QuestionEsDTO`（通过 `QuestionEsDTO.objToDto()`）
3. 按 500 条一批调用 `questionEsDao.saveAll()` 写入 ES
4. 每批次记录日志 `"sync from {} to {}"`

### 4.3 注意事项

- 无 try-catch 包裹 ES 写入操作。如果 ES 不可用，Spring Boot 默认行为是记录异常日志但不阻止启动
- 全表扫描，数据量大时内存占用高
- 不做增量判断，每次都是全量覆盖

## 5. 增量同步 Job（IncSyncQuestionToEs）

**文件**：`job/cycle/IncSyncQuestionToEs.java`

**当前状态**：`@Component` 已注释（第 23-24 行），默认不启用。

```java
// todo 取消注释开启任务
//@Component
```

### 5.1 运行方式

`@Scheduled(fixedRate = 60 * 1000)` —— 每 60 秒执行一次。注意 `@EnableScheduling` 在 MainApplication 上是启用的（第 22 行），但因 `@Component` 被注释，该 Bean 不会注册，调度器无法触发。

### 5.2 变更检测机制

查询过去 5 分钟内有更新的记录：

```java
Date fiveMinutesAgoDate = new Date(new Date().getTime() - 5 * 60 * 1000L);
List<Question> questionList = questionMapper.listQuestionWithDelete(fiveMinutesAgoDate);
```

5 分钟窗口 > 1 分钟执行间隔，确保不会因为执行时间差遗漏记录。

### 5.3 执行流程

1. 计算 5 分钟前时间戳
2. 调用 `questionMapper.listQuestionWithDelete(fiveMinutesAgoDate)` 查询变更（包含软删除记录）
3. 无变更时记录 `"no inc question"` 并返回
4. 有变更时按 500 条一批调用 `questionEsDao.saveAll()` 写入 ES

### 5.4 注意事项

- 同样无 try-catch。执行失败时 Spring Scheduling 会记录异常日志并继续调度后续执行
- 变更检测依赖 `updateTime` 字段，如果有直接操作数据库但不更新 `updateTime` 的情况，增量同步会遗漏

## 6. ES 未开启时的降级行为

### 6.1 搜索降级

**位置**：QuestionController.java:374-390

```java
@PostMapping("/search/page/vo")
public BaseResponse<Page<QuestionVO>> searchQuestionVOByPage(...) {
    Page<Question> questionPage;
    try {
        questionPage = questionService.searchFromEs(questionQueryRequest);
    } catch (Exception e) {
        log.warn("ES 查询失败，降级查询数据库: {}", e.getMessage());
        questionPage = questionService.listQuestionByPage(questionQueryRequest);
    }
    return ResultUtils.success(questionService.getQuestionVOPage(questionPage, request));
}
```

- `/question/search/page/vo`：ES 失败时自动降级到 MySQL 查询，用户无感知
- 其他列表接口（`/list/page`、`/list/page/vo`、`/my/list/page/vo`）始终走 MySQL，不涉及 ES

**对比注意**：帖子搜索接口 `/post/search/page/vo`（PostController.java:227）**没有**降级逻辑，ES 不可用时会直接返回 500 错误。

### 6.2 写操作降级

所有写操作的 ES 同步都包裹在 try-catch 中（`syncQuestionToEs`、`cleanupAfterQuestionDelete`、`batchDeleteQuestions`），ES 不可用时：
- MySQL 写入正常完成
- ES 同步失败只记录 warn 日志
- 不影响接口返回值

### 6.3 `@Document` 注解关闭的影响

**位置**：QuestionEsDTO.java:18-19

```java
// todo 取消注释开启 ES（须先配置 ES）
//@Document(indexName = "question")
```

`@Document` 被注释意味着：
- Spring Data Elasticsearch 不会自动创建或校验 `question` 索引
- 需要手动在 ES 中创建索引并配置映射
- `QuestionEsDao`（继承 `ElasticsearchRepository`）的方法在运行时调用仍可工作（如果索引手动创建了），但不会享受自动 schema 管理

## 7. ES 文档映射

**位置**：QuestionEsDTO.java

| 字段 | Java 类型 | ES 说明 |
|------|----------|--------|
| `id` | `Long` | `@Id`，文档主键 |
| `title` | `String` | 默认 text 类型 |
| `content` | `String` | 默认 text 类型 |
| `answer` | `String` | 默认 text 类型 |
| `tags` | `List<String>` | 来源于 Question.tags（JSON 字符串），转换时解析为列表 |
| `userId` | `Long` | 创建者 ID |
| `createTime` | `Date` | `@Field(type=Date, pattern="yyyy-MM-dd HH:mm:ss")` |
| `updateTime` | `Date` | `@Field(type=Date, pattern="yyyy-MM-dd HH:mm:ss")` |
| `isDelete` | `Integer` | 软删除标记，搜索时过滤 |

转换方法：
- `QuestionEsDTO.objToDto(Question)`：将 tags JSON 字符串解析为 `List<String>`
- `QuestionEsDTO.dtoToObj(QuestionEsDTO)`：将 `List<String>` 序列化回 JSON 字符串

## 8. 一致性风险汇总

| 场景 | 风险 | 当前默认状态下的表现 |
|------|------|-------------------|
| ES 宕机时写入 | 低 | MySQL 不受影响，ES 数据滞后，搜索降级到 MySQL |
| ES 宕机时搜索 | 低 | 题目搜索自动降级；帖子搜索会报 500 |
| AI 生成题目 | 中 | 不触发实时同步，且增量 Job 未开启，ES 中永远搜不到 |
| 单删 vs 批删不一致 | 低 | 单删物理删 ES 文档，批删逻辑删；搜索结果一致，但 ES 存储有冗余 |
| 增量 Job 未开启 | 中 | 任何实时同步失败的记录无法自动补偿 |
| `@Document` 关闭 | 低 | 索引需手动创建，否则首次 ES 操作会失败（但被 try-catch 吞掉） |
