# InterviewSprintPlan 来源与任务状态

> 基于源码分析。核心实现位于 `InterviewSprintPlanServiceImpl`（839 行），涉及 AI 生成每日计划与规则兜底两种策略。

## 1. 数据模型概览

### 1.1 计划表（interview_sprint_plan）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 主键，雪花算法生成 |
| `userId` | Long | 所属用户 |
| `planName` | String | 自动生成，如 "Java, Spring 标签冲刺计划" |
| `duration` | Integer | 计划天数，仅允许 7 或 14 |
| `sourceType` | Integer | 来源类型（0-3），见下文 |
| `sourceId` | Long | 来源 ID（题库 ID 或模拟面试 ID），标签/收藏来源时为 null |
| `startDate` | Date | 开始日期，默认当天 |
| `endDate` | Date | 结束日期 = startDate + duration |
| `totalQuestions` | Integer | 总题目数 |
| `completedCount` | Integer | 已完成天数（每完成一天 +1） |
| `status` | Integer | 计划状态枚举（0/1/2） |
| `isDelete` | Integer | 软删除标记 |

索引：`idx_userId`、`idx_userId_status(userId, status, isDelete)`

### 1.2 任务表（interview_sprint_plan_task）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 主键，雪花算法 |
| `planId` | Long | 所属计划 ID |
| `userId` | Long | 用户 ID（冗余，用于权限校验） |
| `dayNumber` | Integer | 计划中的第几天，从 1 开始 |
| `taskDate` | Date | 该任务对应的日历日期 |
| `questionIds` | String | JSON 数组，如 `[1001, 1002, 1003]` |
| `postIds` | String | JSON 数组，帖子 ID 列表 |
| `mockInterviewGoal` | String | 当天模拟面试目标描述 |
| `studyNotes` | String | 学习笔记和提示 |
| `status` | Integer | 任务状态（0/1） |
| `completedTime` | Date | 完成时间戳，未完成时为 null |
| `isDelete` | Integer | 软删除标记 |

索引：`idx_planId`、`idx_userId_taskDate(userId, taskDate)`

## 2. 四种来源类型

**枚举定义**：`SprintPlanSourceTypeEnum`

| 枚举值 | 值 | 中文 | 必填参数 |
|--------|---|------|---------|
| `QUESTION_BANK` | 0 | 题库 | `sourceId`（题库 ID） |
| `TAG` | 1 | 标签 | `tags`（标签列表） |
| `FAVOURITED_POST` | 2 | 收藏帖子 | 无（自动取当前用户收藏） |
| `MOCK_INTERVIEW` | 3 | 模拟面试记录 | `sourceId`（模拟面试 ID） |

### 2.1 题库来源（sourceType=0）

**素材拉取逻辑**（InterviewSprintPlanServiceImpl.java:219-234）：

1. 查 `QuestionBankQuestion` 关联表，条件 `questionBankId == request.getSourceId()`
2. 提取所有 `questionId`
3. 通过 `questionService.listByIds(qIds)` 批量获取题目实体

**帖子**：不拉取（返回空列表）。
**面试摘要**：不生成。

**参数校验**（第 85-87 行）：`sourceId` 为 null 时抛出参数错误异常。

### 2.2 标签来源（sourceType=1）

**素材拉取逻辑**（InterviewSprintPlanServiceImpl.java:235-258）：

1. 构建 `LambdaQueryWrapper<Question>`
2. 对每个 tag 添加 `OR LIKE` 条件（匹配 `Question.tags` JSON 字段）
3. 限制最多返回 500 条结果

示例：tags=["Java", "Spring"] 生成的 SQL 条件为 `tags LIKE '%Java%' OR tags LIKE '%Spring%'`。

**帖子**：不拉取。
**面试摘要**：不生成。

**参数校验**（第 88-89 行）：`tags` 为 null 或空列表时抛出参数错误异常。

### 2.3 收藏帖子来源（sourceType=2）

**素材拉取逻辑**（InterviewSprintPlanServiceImpl.java:276-288）：

1. 调用 `postFavourService.listFavourPostByPage()` 获取当前用户收藏的帖子
2. 固定取第 1 页，每页 100 条

**题目**：不拉取（显式返回 `Collections.emptyList()`）。
**面试摘要**：不生成。

**参数校验**：无额外参数要求。

### 2.4 模拟面试来源（sourceType=3）

**素材拉取逻辑**（InterviewSprintPlanServiceImpl.java:259-270 + 293-306）：

**题目**：
1. 通过 `sourceId` 加载 `MockInterview` 记录
2. 解析面试记录的 `messages` JSON，提取 `jobPosition`（岗位名称）
3. 用 `jobPosition` 作为关键词在 `Question` 表做 LIKE 搜索（匹配 title 或 tags）
4. 限制最多返回 200 条结果

**帖子**：不拉取。

**面试摘要**（`summarizeMockInterview`，第 348-372 行）：
- 拼接岗位名称、工作年限、难度、对话轮次
- 截取最后一条消息的前 200 字符作为摘要
- 该摘要传给 AI 作为生成每日计划的参考上下文

**参数校验**（第 91-93 行）：`sourceId` 为 null 时抛出参数错误异常。

## 3. 每日计划生成策略

### 3.1 AI 生成（generateByAI）

**位置**：InterviewSprintPlanServiceImpl.java:394-455

流程：
1. 构建系统提示词（面试辅导专家角色）
2. 构建用户提示词，包含：
   - 所有可用题目（最多 200 条）的 ID、标题、标签
   - 所有可用帖子（最多 50 条）
   - 面试摘要（仅模拟面试来源）
3. 要求 AI 返回严格 JSON 数组，每个元素对应一天，包含 `questionIds`、`postIds`、`mockInterviewGoal`、`studyNotes`
4. 解析 AI 返回的 JSON 并校验所有 ID 的合法性

**ID 校验**（第 460-528 行）：AI 返回的 `questionIds` 和 `postIds` 会与素材池中的有效 ID 集合做交集过滤，防止 AI 幻觉产生不存在的 ID。

### 3.2 规则兜底（generateByRule）

**位置**：InterviewSprintPlanServiceImpl.java:535-585

当 AI 生成失败时使用：
1. 随机打乱题目和帖子顺序
2. 按天数均分（questionsPerDay = total / duration）
3. 最后一天分配所有剩余题目/帖子
4. 根据每天题目的标签自动生成模拟面试目标

## 4. 计划状态枚举

**枚举定义**：`SprintPlanStatusEnum`

| 枚举值 | 值 | 中文 | 触发条件 |
|--------|---|------|---------|
| `IN_PROGRESS` | 0 | 进行中 | 创建时的初始状态 |
| `COMPLETED` | 1 | 已完成 | `completedCount >= duration` 时自动流转 |
| `ABANDONED` | 2 | 已废弃 | 手动设置（用于释放防重锁） |

**状态流转**：

```
创建 → IN_PROGRESS(0)
         ↓ 完成所有天数
       COMPLETED(1)

IN_PROGRESS(0)
         ↓ 用户主动废弃
       ABANDONED(2)
```

自动完成逻辑（InterviewSprintPlanServiceImpl.java:670-672）：

```java
if (plan.getCompletedCount() >= plan.getDuration()) {
    plan.setStatus(SprintPlanStatusEnum.COMPLETED.getValue());
}
```

## 5. 任务状态枚举

**枚举定义**：`SprintPlanTaskStatusEnum`

| 枚举值 | 值 | 中文 | 说明 |
|--------|---|------|------|
| `PENDING` | 0 | 未完成 | 创建时的初始状态 |
| `COMPLETED` | 1 | 已完成 | 用户通过 `/sprint-plan/complete` 接口标记 |

**完成任务逻辑**（InterviewSprintPlanServiceImpl.java:644-675）：

1. 校验任务存在且属于当前用户
2. **幂等检查**：如果任务已是 COMPLETED 状态，直接返回，不会重复累加 `completedCount`（第 658-660 行）
3. 设置任务状态为 COMPLETED，记录 `completedTime`
4. 计划的 `completedCount` 加 1
5. 如果 `completedCount >= duration`，自动将计划状态流转为 COMPLETED

**重新生成时的状态重置**（InterviewSprintPlanServiceImpl.java:765-766）：
当对薄弱日重新生成任务时，状态会被重置为 PENDING，`completedTime` 清空。

## 6. 防重复规则

### 6.1 计划级防重

**位置**：InterviewSprintPlanServiceImpl.java:111-130（`checkDuplicate` 方法）

查询条件：
- `userId` = 当前用户
- `sourceType` = 请求中的来源类型
- `status` = IN_PROGRESS（值 0）
- `sourceId` = 请求中的来源 ID（为 null 时用 `IS NULL` 匹配）

如果存在匹配记录，抛出异常：`"已存在进行中的同类计划，请先完成或废弃后再创建"`。

**含义**：
- 同一用户、同一来源类型、同一来源 ID，最多只能有一个进行中的计划
- 已完成或已废弃的计划不占用限额
- 不同来源类型之间互不影响（如可以同时有一个题库计划和一个标签计划）
- sourceId 为 null 的来源类型（标签、收藏帖子）使用 `IS NULL` 匹配，即同一来源类型最多一个活跃计划

### 6.2 题目级防重（重新生成时）

**位置**：InterviewSprintPlanServiceImpl.java:698-775

重新生成薄弱日任务时的去重逻辑：

1. 收集计划中所有天的已分配 `questionIds`，形成已用 ID 集合
2. 从原始来源重新拉取题目
3. 过滤掉已用 ID 集合中的题目
4. 每生成一天的任务后，将该天新分配的 ID 也加入已用集合，防止同一次重新生成中跨天重复

### 6.3 AI 返回值校验

**位置**：InterviewSprintPlanServiceImpl.java:460-528

AI 返回的每日计划中的 `questionIds` 和 `postIds` 会与素材池有效 ID 集合做过滤。不在有效集合中的 ID 被静默丢弃，防止 AI 幻觉引入不存在的题目。

## 7. API 接口一览

**Controller**：`InterviewSprintPlanController`，基础路径 `/sprint-plan`

| 方法 | 路径 | HTTP | 说明 |
|------|------|------|------|
| `createSprintPlan` | `/sprint-plan/create` | POST | 创建冲刺计划 |
| `getTodayTask` | `/sprint-plan/today` | GET | 获取指定计划的今日任务 |
| `completeTask` | `/sprint-plan/complete` | POST | 标记某个任务为已完成 |
| `regenerateWeakTagTasks` | `/sprint-plan/regenerate` | POST | 重新生成薄弱日任务 |
| `getSprintPlanDetail` | `/sprint-plan/detail` | GET | 获取计划详情（含所有任务） |
| `listSprintPlanByPage` | `/sprint-plan/list/page/vo` | POST | 分页查询我的计划列表（pageSize 上限 20） |

## 8. 计划创建完整流程

```
1. 参数校验（validCreateRequest）
   - duration 必须是 7 或 14
   - sourceType 必须在 0-3 范围
   - sourceType=0 或 3 时 sourceId 必填
   - sourceType=1 时 tags 必填
2. 获取当前登录用户
3. 防重检查（checkDuplicate）
4. 按来源类型拉取素材
   - gatherQuestions() → 题目列表
   - gatherPosts() → 帖子列表
   - gatherInterviewSummary() → 面试摘要字符串
5. 校验素材不为空
6. 计算 startDate / endDate
7. 生成每日计划
   - 优先 AI 生成（generateByAI）
   - 失败时降级到规则生成（generateByRule）
8. 持久化 InterviewSprintPlan
9. 批量创建 InterviewSprintPlanTask（每天一条）
10. 返回计划 ID
```
