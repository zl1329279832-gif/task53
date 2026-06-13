# InterviewSprintPlan 来源与任务状态

## 1. 数据模型概览

面试冲刺计划由两张表组成：

| 表 | 实体类 | 说明 |
|----|--------|------|
| `interview_sprint_plan` | `InterviewSprintPlan` | 计划主体：来源、天数、进度、状态 |
| `interview_sprint_plan_task` | `InterviewSprintPlanTask` | 每日任务：题目 ID 列表、帖子 ID 列表、模拟面试目标 |

两张表均使用雪花 ID（`@TableId(type = IdType.ASSIGN_ID)`）和 `@TableLogic` 软删除。

`userId` 在 task 表中冗余存储，目的是权限校验时不需要 JOIN plan 表。

## 2. 四种来源类型（SprintPlanSourceTypeEnum）

```java
public enum SprintPlanSourceTypeEnum {
    QUESTION_BANK("题库", 0),
    TAG("标签", 1),
    FAVOURITED_POST("收藏帖子", 2),
    MOCK_INTERVIEW("模拟面试记录", 3);
}
```

### 2.1 QUESTION_BANK（题库，sourceType=0）

**素材拉取逻辑**：

```
QuestionBankQuestion 关联表 ──(WHERE questionBankId = sourceId)──► questionId 列表
                                                                   │
                                                                   ▼
                                              questionService.listByIds(questionIds)
                                                                   │
                                                                   ▼
                                                            List<Question>
```

- 从 `question_bank_question` 关联表查出该题库绑定的所有题目 ID
- 再通过 `questionService.listByIds()` 批量获取题目实体
- **必须提供 `sourceId`**（题库 ID），否则参数校验失败

**Posts**：不拉取（`gatherPosts` 返回空列表）

### 2.2 TAG（标签，sourceType=1）

**素材拉取逻辑**：

```
request.tags = ["Java", "Redis", ...]
         │
         ▼
对每个 tag 构造 LIKE '%tag%' 条件，OR 连接
         │
         ▼
LIMIT 500 条 Question
```

- 使用 MyBatis-Plus `LambdaQueryWrapper` 对 `Question.tags` 字段做多个 `LIKE` 查询（OR 语义）
- 最多取 500 条，超出截断
- **必须提供 `tags` 列表**（非空），否则参数校验失败
- **`sourceId` 为 null**

**Posts**：不拉取

**防重复影响**：由于 `sourceId` 为 null，防重复规则变为 `(userId, sourceType=1, sourceId IS NULL, status=IN_PROGRESS)` — 即同一用户同时只能有一个进行中的标签计划，**不管选了什么标签**。

### 2.3 FAVOURITED_POST（收藏帖子，sourceType=2）

**素材拉取逻辑**：

```
postFavourService.listFavourPostByPage(userId, page=1, size=100)
         │
         ▼
List<Post>（最多 100 条）
```

- 从用户收藏的帖子中分页取前 100 条
- **Questions**：不拉取（`gatherQuestions` 返回空列表）
- `sourceId` 为 null

### 2.4 MOCK_INTERVIEW（模拟面试记录，sourceType=3）

**素材拉取逻辑**：

```
mockInterviewService.getById(sourceId)
         │
         ├── 校验 userId 归属
         │
         ├──► gatherQuestions：用 jobPosition 作为关键词
         │    搜索 Question 的 title 和 tags（LIKE），LIMIT 200
         │
         └──► gatherInterviewSummary：拼接面试摘要文本
              包含：岗位、工作年限、难度、对话轮数、最后一条消息截断
```

- **必须提供 `sourceId`**（模拟面试记录 ID）
- 会校验该面试记录是否属于当前用户
- 题目拉取以面试岗位（`jobPosition`）为关键词做模糊搜索
- 额外生成一段面试摘要文本，作为 AI 生成计划时的上下文

### 2.5 各来源素材矩阵

| 来源 | sourceId | Questions | Posts | Interview Summary |
|------|----------|-----------|-------|-------------------|
| QUESTION_BANK | **必填** | 题库关联题目 | - | - |
| TAG | null | 按标签 LIKE 搜索 (≤500) | - | - |
| FAVOURITED_POST | null | - | 用户收藏帖子 (≤100) | - |
| MOCK_INTERVIEW | **必填** | 按岗位关键词搜索 (≤200) | - | 面试摘要文本 |

**三路素材至少一路非空**，否则抛异常（`"获取的素材为空，无法创建计划"`）。

## 3. 计划生成流程

```
createSprintPlan(request)
    │
    ├── 1. validCreateRequest()         参数校验
    ├── 2. checkDuplicate()             防重复检查
    ├── 3. gatherQuestions()            拉取题目
    ├── 4. gatherPosts()                拉取帖子
    ├── 5. gatherInterviewSummary()     拉取面试摘要
    ├── 6. 三路素材全空 → 抛异常
    ├── 7. generateDailyPlan()
    │       ├── 7a. generateByAI()      AI 生成（优先）
    │       └── 7b. generateByRule()    规则生成（AI 失败时兜底）
    ├── 8. 保存 InterviewSprintPlan     status = IN_PROGRESS
    └── 9. 批量保存 InterviewSprintPlanTask (saveBatch, 500/批)
```

整个方法标注 `@Transactional(rollbackFor = Exception.class)`。

### 3.1 AI 生成（generateByAI）

- 构造 system prompt（角色："面试辅导教练"），要求 AI：
  - 将题目均匀分配到每天
  - 按难度递进排列（先基础后高级）
  - 每天分配 2-3 篇帖子作为补充阅读
  - 为每天设定具体可衡量的模拟面试目标
  - 输出严格 JSON 数组格式
- 最多传入 200 个题目摘要和 50 个帖子摘要
- 调用 `aiManager.doChat(systemPrompt, userPrompt)`（火山引擎 Ark / DeepSeek V3）
- 解析 AI 返回的 JSON，用 `parseAndValidateAIResponse` 做 ID 白名单校验（防止 AI 捏造不存在的题目/帖子 ID）
- AI 返回天数不足时用空项补齐，超出时截断

### 3.2 规则生成（generateByRule，兜底）

当 AI 调用抛任何异常时自动降级：

- 随机打乱题目和帖子列表
- 计算 `qPerDay = totalQuestions / duration`，`pPerDay = totalPosts / duration`
- 均匀分配，最后一天取剩余全部（处理整除不尽的情况）
- 模拟面试目标从当天题目的 tags 中提取关键词生成
- 学习笔记使用模板：`"第 X/Y 天，今天需要完成 N 道题目和 M 篇阅读"`

## 4. 状态枚举

### 4.1 计划状态（SprintPlanStatusEnum）

```java
public enum SprintPlanStatusEnum {
    IN_PROGRESS("进行中", 0),
    COMPLETED("已完成", 1),
    ABANDONED("已废弃", 2);
}
```

**状态流转**：

```
                     completeTask 触发
  IN_PROGRESS ──────────────────────────► COMPLETED
  (created)     当 completedCount         (终态)
                >= duration 时自动转移

  IN_PROGRESS ──────────────────────────► ABANDONED
  (created)     无 API 端点，             (终态)
                仅测试中直接 updateById
```

**关键细节**：
- `IN_PROGRESS → COMPLETED`：在 `completeTask()` 方法中，当 `completedCount >= duration` 时自动转移，不需要用户手动操作
- `IN_PROGRESS → ABANDONED`：枚举值已定义，但 **Controller 没有暴露"废弃计划"的 API 端点**。目前只能通过直接操作数据库或测试代码中的 `updateById()` 来废弃计划

### 4.2 任务状态（SprintPlanTaskStatusEnum）

```java
public enum SprintPlanTaskStatusEnum {
    PENDING("未完成", 0),
    COMPLETED("已完成", 1);
}
```

**状态流转**：

```
  PENDING ────── completeTask() ──────► COMPLETED
  (created)                            (终态)
```

## 5. 任务完成逻辑（completeTask）

```java
@Transactional(rollbackFor = Exception.class)
public void completeTask(InterviewSprintPlanTaskCompleteRequest request) {
    // 1. 查询任务，校验归属
    // 2. 幂等检查：如果已经 COMPLETED，直接 return
    if (task.getStatus() == SprintPlanTaskStatusEnum.COMPLETED.getValue()) {
        return;  // 不重复计数
    }
    // 3. 标记任务完成
    task.setStatus(COMPLETED);
    task.setCompletedTime(now);
    // 4. plan.completedCount++
    plan.setCompletedCount(plan.getCompletedCount() + 1);
    // 5. 如果 completedCount >= duration → plan.status = COMPLETED
    if (plan.getCompletedCount() >= plan.getDuration()) {
        plan.setStatus(SprintPlanStatusEnum.COMPLETED.getValue());
    }
}
```

**幂等保证**：重复调用 `completeTask` 不会导致 `completedCount` 被重复累加。

## 6. 防重复规则（checkDuplicate）

```java
private void checkDuplicate(InterviewSprintPlanCreateRequest request, Long userId) {
    LambdaQueryWrapper<InterviewSprintPlan> queryWrapper = Wrappers.lambdaQuery(InterviewSprintPlan.class)
            .eq(InterviewSprintPlan::getUserId, userId)
            .eq(InterviewSprintPlan::getSourceType, request.getSourceType())
            .eq(InterviewSprintPlan::getStatus, SprintPlanStatusEnum.IN_PROGRESS.getValue());
    if (request.getSourceId() != null) {
        queryWrapper.eq(InterviewSprintPlan::getSourceId, request.getSourceId());
    } else {
        queryWrapper.isNull(InterviewSprintPlan::getSourceId);
    }
    long count = this.count(queryWrapper);
    ThrowUtils.throwIf(count > 0, ErrorCode.OPERATION_ERROR,
            "已存在进行中的同类计划，请先完成或废弃后再创建");
}
```

**防重复维度**：`(userId, sourceType, sourceId, status=IN_PROGRESS)`

| 来源类型 | sourceId | 实际防重复粒度 |
|---------|----------|---------------|
| QUESTION_BANK | 题库 ID | 同一用户 + 同一题库 + 进行中 → 阻止 |
| TAG | null | 同一用户 + 任意标签 + 进行中 → 阻止（只允许一个标签计划） |
| FAVOURITED_POST | null | 同一用户 + 收藏帖子 + 进行中 → 阻止 |
| MOCK_INTERVIEW | 面试记录 ID | 同一用户 + 同一面试记录 + 进行中 → 阻止 |

**注意事项**：
- 仅检查 `status = IN_PROGRESS` 的计划。已 COMPLETED 或 ABANDONED 的计划不阻止新建
- TAG 来源因为 `sourceId` 固定为 null，所以同一用户即使选了完全不同的标签组，也无法创建第二个进行中的标签计划

## 7. 重新生成弱项日任务（regenerateWeakTagTasks）

```
regenerateWeakTagTasks(planId, dayNumbers?)
    │
    ├── dayNumbers 为空 → 自动识别所有 status=PENDING 的天
    │
    ├── 收集已使用的 questionId/postId 集合
    │
    ├── 从原始来源重新拉取素材，排除已用 ID
    │
    ├── generateByRule() 重新分配（仅规则生成，不走 AI）
    │
    └── 重置目标天：status=PENDING, completedTime=null
```

**关键限制**：
- 重新生成只走规则引擎（`generateByRule`），不再调用 AI
- 已完成的天的题目不会被重新分配（从候选池中排除）
- 如果原始来源的素材已被用尽（所有题目都已分配），重新生成的天可能没有题目

## 8. REST API 端点

| Method | Path | 说明 | 鉴权 |
|--------|------|------|------|
| POST | `/sprint-plan/create` | 创建计划 | 登录用户 |
| GET | `/sprint-plan/today` | 获取今天的任务 | 登录用户 + 归属校验 |
| POST | `/sprint-plan/complete` | 标记任务完成 | 登录用户 + 归属校验 |
| POST | `/sprint-plan/regenerate` | 重新生成弱项天 | 登录用户 + 归属校验 |
| GET | `/sprint-plan/detail` | 获取计划详情（含所有任务） | 登录用户 + 归属校验 |
| POST | `/sprint-plan/list/page/vo` | 分页查询我的计划 | 登录用户 |

所有涉及 planId 的接口都会校验 `plan.getUserId() == loginUser.getId()`，跨用户访问抛 `NO_AUTH_ERROR`。

## 9. VO 扩展字段

### InterviewSprintPlanVO

- `progressPercent`：`completedCount * 100 / duration`，整数百分比
- `tasks`：仅在详情接口中填充

### InterviewSprintPlanTaskVO

- `questions: List<QuestionVO>`：从 `questionIds`（JSON 字符串）解析后批量查询
- `posts: List<QuestionVO>`：从 `postIds`（JSON 字符串）解析后批量查询
- 解析时保持原始顺序（按 JSON 数组中的位置）
