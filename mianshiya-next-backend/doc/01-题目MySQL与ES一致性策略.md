# 题目 MySQL 与 ES 一致性策略

## 1. 总体架构

题目数据以 MySQL 为唯一真实源（Source of Truth），Elasticsearch 作为只读搜索副本。写入链路采用 **best-effort** 模式：ES 操作全部 try/catch，失败仅 `log.warn`，不阻断主流程事务。

```
┌──────────────────────────────────────────────────────────────────────┐
│                        写入路径（MySQL 优先）                          │
│                                                                      │
│  Controller.add/update/edit ──► questionService.save/updateById      │
│                                ──► syncQuestionToEs()  [try/catch]   │
│                                                                      │
│  Controller.delete           ──► questionService.removeById          │
│                                ──► cleanupAfterQuestionDelete()      │
│                                     └─ questionEsDao.deleteById()    │
│                                                                      │
│  Controller.batchDelete      ──► batchDeleteQuestions() @Tx          │
│                                ──► questionEsDao.saveAll(isDelete=1) │
│                                                                      │
├──────────────────────────────────────────────────────────────────────┤
│                        读取路径（ES 优先 + DB 降级）                    │
│                                                                      │
│  searchQuestionVOByPage      ──► searchFromEs()                      │
│                                ──► catch → listQuestionByPage()      │
│                                                                      │
├──────────────────────────────────────────────────────────────────────┤
│                        补偿路径（Job，默认全部注释关闭）                 │
│                                                                      │
│  FullSyncQuestionToEs   (CommandLineRunner, 启动时全量)               │
│  IncSyncQuestionToEs    (@Scheduled 60s, 增量 5 分钟窗口)             │
└──────────────────────────────────────────────────────────────────────┘
```

## 2. CRUD 实时同步细节

### 2.1 新增（addQuestion）

`QuestionController.addQuestion()` 在 `questionService.save(question)` 成功后，直接用内存中已填充的 `Question` 对象调用 `syncQuestionToEs(question)`。

```java
// QuestionController.java L84-89
boolean result = questionService.save(question);
ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
long newQuestionId = question.getId();
questionService.syncQuestionToEs(question);
```

**注意**：此处未重新查询数据库，依赖 `save()` 回填的雪花 ID 和内存中的字段值。如果 MyBatis-Plus 自动填充了 `createTime`/`updateTime`，这些值在传入 ES 时可能为 null（取决于 `MetaObjectHandler` 是否回写到实体）。

### 2.2 更新（updateQuestion / editQuestion）

两个接口逻辑一致：先 `updateById`，再 **重新 `getById` 查全量数据** 后同步到 ES。

```java
// QuestionController.java L149-155
boolean result = questionService.updateById(question);
ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
Question fullQuestion = questionService.getById(id);
if (fullQuestion != null) {
    questionService.syncQuestionToEs(fullQuestion);
}
```

`editQuestion`（L362-368）逻辑完全相同。重新查询是为了保证 ES 中存储的是完整文档，而不是仅包含本次更新字段的 partial 对象。

### 2.3 单条删除（deleteQuestion）

调用 `cleanupAfterQuestionDelete(questionId)`，该方法做了两件事：

1. 删除 `question_bank_question` 关联表中的绑定关系
2. `questionEsDao.deleteById(questionId)` — 从 ES 索引中物理删除文档

```java
// QuestionServiceImpl.java L468-479
public void cleanupAfterQuestionDelete(Long questionId) {
    // 1. 删关联表
    questionBankQuestionService.remove(lambdaQueryWrapper);
    // 2. 物理删除 ES 文档
    try {
        questionEsDao.deleteById(questionId);
    } catch (Exception e) {
        log.warn("删除题目 {} 的 ES 数据失败: {}", questionId, e.getMessage());
    }
}
```

**注意**：MySQL 侧是 `@TableLogic` 软删除（`isDelete` 置 1），但 ES 侧是物理删除。这是一个不一致点 — 增量同步 Job 拉取已软删记录写入 ES 时带 `isDelete=1`，而直接删除走的是 `deleteById`。两种方式不会冲突，但在增量 Job 开启期间，已删除的题目在 ES 中可能先被物理删除、后被增量 Job 以 `isDelete=1` 重新写入。

### 2.4 批量删除（batchDeleteQuestions）

`@Transactional` 方法，循环逐条从 MySQL 软删除 + 清理关联表，最后批量向 ES 写入 `isDelete=1` 标记（不是物理删除）。

```java
// QuestionServiceImpl.java L370-378
List<QuestionEsDTO> esDTOList = questionIdList.stream().map(id -> {
    QuestionEsDTO dto = new QuestionEsDTO();
    dto.setId(id);
    dto.setIsDelete(1);
    return dto;
}).collect(Collectors.toList());
questionEsDao.saveAll(esDTOList);
```

**与单条删除的差异**：单条删除用 `deleteById` 物理移除 ES 文档，批量删除用 `saveAll` 标记 `isDelete=1`。ES 搜索方法 `searchFromEs` 固定过滤 `isDelete=0`，所以两种方式对搜索结果都有效，但批量删除后的文档仍存在于索引中。

### 2.5 syncQuestionToEs 的 warn 行为

```java
// QuestionServiceImpl.java L458-465
public void syncQuestionToEs(Question question) {
    try {
        QuestionEsDTO questionEsDTO = QuestionEsDTO.objToDto(question);
        questionEsDao.save(questionEsDTO);
    } catch (Exception e) {
        log.warn("同步题目 {} 到 ES 失败: {}", question.getId(), e.getMessage());
    }
}
```

**每次 add/update/edit 都会触发此方法**。如果 ES 未启用（`QuestionEsDTO` 的 `@Document` 注解被注释、或 ES 服务不可达、或 `QuestionEsDao` Bean 未正常创建），`questionEsDao.save()` 会抛异常，被 catch 后输出 warn 日志。**这意味着即使 ES 未启用，每次题目 CRUD 操作都会在日志中产生一条 warn。** 这不影响业务正确性，但会造成日志噪音。

## 3. 软删除与 ES 的交互

- **Question 实体**：使用 MyBatis-Plus `@TableLogic`，`isDelete` 字段，0=未删，1=已删。`this.removeById()` 实际执行 `UPDATE question SET isDelete=1`。
- **QuestionMapper 自定义查询**：`listAllQuestionWithDelete()` 和 `listQuestionWithDelete(Date)` 使用原生 `@Select` 注解，**绕过了 `@TableLogic` 过滤**，能查出已软删的记录。这是专门给同步 Job 设计的，目的是让 ES 也能标记已删数据。
- **searchFromEs** 查询条件固定包含 `termQuery("isDelete", 0)`，确保搜索结果不包含已删题目。

## 4. FullSyncQuestionToEs（全量同步）

**文件**：`job/once/FullSyncQuestionToEs.java`
**当前状态**：**注释关闭**（`//@Component`）
**触发时机**：实现 `CommandLineRunner`，Spring 容器启动完成后自动执行一次

```java
// todo 取消注释开启任务
//@Component
public class FullSyncQuestionToEs implements CommandLineRunner {
    @Override
    public void run(String... args) {
        List<Question> questionList = questionMapper.listAllQuestionWithDelete();
        // ... 转换为 QuestionEsDTO ...
        // 每 500 条批量写入 ES
        questionEsDao.saveAll(questionEsDTOList.subList(i, end));
    }
}
```

**职责**：
- 拉取 MySQL 全量数据（**包含已软删记录**），一次性写入 ES
- 用于首次部署 ES 后的初始数据灌入，或在增量同步丢失大量数据后做兜底校准
- 每次启动都会执行，重复写入（ES upsert 语义，幂等）

**前置条件**：
1. `QuestionEsDTO` 的 `@Document(indexName = "question")` 注解已取消注释
2. Elasticsearch 服务可达
3. 如果索引不存在，需提前创建或由 Spring Data Elasticsearch 自动创建

## 5. IncSyncQuestionToEs（增量同步）

**文件**：`job/cycle/IncSyncQuestionToEs.java`
**当前状态**：**注释关闭**（`//@Component`）
**触发时机**：`@Scheduled(fixedRate = 60 * 1000)` — 每 60 秒执行一次

```java
// todo 取消注释开启任务
//@Component
public class IncSyncQuestionToEs {
    @Scheduled(fixedRate = 60 * 1000)
    public void run() {
        long FIVE_MINUTES = 5 * 60 * 1000L;
        Date fiveMinutesAgoDate = new Date(new Date().getTime() - FIVE_MINUTES);
        List<Question> questionList = questionMapper.listQuestionWithDelete(fiveMinutesAgoDate);
        // ... 每 500 条批量写入 ES ...
    }
}
```

**职责**：
- 补偿实时同步的遗漏：查询 `updateTime >= 当前时间 - 5 分钟` 的所有记录（含软删），批量写入 ES
- 5 分钟窗口 > 1 分钟执行间隔，形成重叠覆盖，减少漏同步
- 使用 `listQuestionWithDelete(Date)` 绕过 `@TableLogic`，软删记录也会被同步到 ES（带 `isDelete=1`）

**适用场景**：
- 实时同步因 ES 短暂不可用而 warn 丢失后，增量 Job 在下一个周期自动补齐
- 批量导入/修改数据后未走 Controller 的 CRUD 链路时，增量 Job 可兜底

## 6. Post 的同步机制（同构）

`PostEsDTO`、`FullSyncPostToEs`、`IncSyncPostToEs`、`PostServiceImpl.syncPostToEs()` 与题目侧完全对称，这里不重复描述。差异点：
- Post 索引名为 `"post"`
- Post 的批量删除未在 Controller 中暴露（仅有管理员单条删除）

## 7. ES 未启用时的降级行为

### 7.1 什么算"ES 未启用"

以下任一条件成立即视为 ES 未完整启用：

| 条件 | 当前代码状态 |
|------|------------|
| `QuestionEsDTO` 的 `@Document(indexName = "question")` | **已注释**（`//@Document`） |
| `PostEsDTO` 的 `@Document(indexName = "post")` | **已注释** |
| 4 个同步 Job 的 `@Component` | **已注释**（`//@Component`） |
| `MainApplication` 的 `@SpringBootApplication` | 正常（未 exclude ES） |
| `application.yml` 的 `spring.elasticsearch.uris` | 配置了 `localhost:9200` |

`@Document` 注解被注释意味着 Spring Data Elasticsearch 不会将 `QuestionEsDTO` 识别为有效的文档实体。`QuestionEsDao`（继承 `ElasticsearchRepository<QuestionEsDTO, Long>`）的 Bean 创建可能失败或行为异常。

### 7.2 各操作降级表现

| 操作 | 调用链 | ES 未启用时表现 |
|------|--------|---------------|
| 新增题目 | `addQuestion` → `syncQuestionToEs` | `questionEsDao.save()` 抛异常 → catch → `log.warn` → 不影响新增结果 |
| 更新题目 | `updateQuestion` → `syncQuestionToEs` | 同上，warn 日志，不影响更新 |
| 编辑题目 | `editQuestion` → `syncQuestionToEs` | 同上 |
| 删除题目 | `deleteQuestion` → `cleanupAfterQuestionDelete` | `questionEsDao.deleteById()` 抛异常 → catch → warn |
| 批量删除 | `batchDeleteQuestions` | `questionEsDao.saveAll()` 抛异常 → catch → warn |
| **搜索题目** | `searchQuestionVOByPage` → `searchFromEs` | `elasticsearchRestTemplate.search()` 抛异常 → **Controller 层 catch → 降级到 `listQuestionByPage`（MySQL 查询）** |
| 启动全量同步 | `FullSyncQuestionToEs.run()` | **Job 被注释，不会执行**。若取消注释但 ES 不可用，启动时 run 方法内 saveAll 会抛异常导致启动失败 |
| 定时增量同步 | `IncSyncQuestionToEs.run()` | **Job 被注释，不会执行** |

### 7.3 关键结论

1. **ES 未启用不影响任何写操作**：所有 ES 写入都是 fire-and-forget，MySQL 事务不受影响。
2. **搜索自动降级到 MySQL**：`searchQuestionVOByPage` 在 Controller 层有 try/catch，ES 失败后回退到 `listQuestionByPage`（基于 MyBatis-Plus `LIKE` 查询），功能可用但全文检索能力退化为 SQL LIKE 模糊匹配。
3. **warn 日志无法关闭**：只要 `syncQuestionToEs` 方法未被注释，每次 CRUD 操作必然产生 warn。如果确定不启用 ES，建议将 Controller 中 3 处 `syncQuestionToEs` 调用和 1 处 `cleanupAfterQuestionDelete` 调用注释掉以消除日志噪音。
4. **启动不会失败**：因为 `@Document` 被注释，Spring Data Elasticsearch 的行为取决于版本 — 在 Spring Boot 2.7.x 中，如果 ES 连接配置存在但 `@Document` 未标注，`ElasticsearchRepository` 的 Bean 创建仍可能成功（通过 generic type 推断），只是运行时操作报错。如果连 ES 服务都不可达，Spring Boot 的 ES auto-configuration 会创建 `ElasticsearchRestTemplate` 但延迟到实际调用时才报连接错误。

## 8. 启用 ES 的操作清单

如果决定启用 ES 搜索，需要按以下顺序操作：

1. **取消 `QuestionEsDTO` 的 `@Document` 注释**
2. **取消 `PostEsDTO` 的 `@Document` 注释**（如需 Post 搜索）
3. 确保 Elasticsearch 服务正在运行
4. 取消 `FullSyncQuestionToEs` 的 `@Component` 注释，重启应用做一次全量灌入
5. 灌入完成后，可注释回 `FullSyncQuestionToEs`（避免每次启动重复全量同步）
6. 取消 `IncSyncQuestionToEs` 的 `@Component` 注释，开启增量补偿
7. Post 侧同理
