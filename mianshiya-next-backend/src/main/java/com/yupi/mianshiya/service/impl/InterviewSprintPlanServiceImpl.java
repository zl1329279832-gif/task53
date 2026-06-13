package com.yupi.mianshiya.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.mapper.InterviewSprintPlanMapper;
import com.yupi.mianshiya.manager.AiManager;
import com.yupi.mianshiya.model.dto.sprintplan.*;
import com.yupi.mianshiya.model.entity.*;
import com.yupi.mianshiya.model.enums.SprintPlanSourceTypeEnum;
import com.yupi.mianshiya.model.enums.SprintPlanStatusEnum;
import com.yupi.mianshiya.model.enums.SprintPlanTaskStatusEnum;
import com.yupi.mianshiya.model.vo.sprintplan.InterviewSprintPlanTaskVO;
import com.yupi.mianshiya.model.vo.sprintplan.InterviewSprintPlanVO;
import com.yupi.mianshiya.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 面试冲刺计划服务实现
 */
@Slf4j
@Service
public class InterviewSprintPlanServiceImpl
        extends ServiceImpl<InterviewSprintPlanMapper, InterviewSprintPlan>
        implements InterviewSprintPlanService {

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private QuestionService questionService;

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    @Lazy
    private PostFavourService postFavourService;

    @Resource
    private MockInterviewService mockInterviewService;

    @Resource
    private AiManager aiManager;

    @Resource
    @Lazy
    private InterviewSprintPlanTaskService planTaskService;

    // ======================= 参数校验 ========================

    @Override
    public void validCreateRequest(InterviewSprintPlanCreateRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Integer duration = request.getDuration();
        if (duration == null || (duration != 7 && duration != 14)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "计划天数只能为 7 或 14");
        }
        Integer sourceType = request.getSourceType();
        SprintPlanSourceTypeEnum sourceTypeEnum = SprintPlanSourceTypeEnum.getEnumByValue(
                sourceType != null ? sourceType : -1);
        if (sourceTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "来源类型不合法");
        }
        if (sourceTypeEnum == SprintPlanSourceTypeEnum.QUESTION_BANK && request.getSourceId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "题库来源需指定题库 id");
        }
        if (sourceTypeEnum == SprintPlanSourceTypeEnum.TAG && CollUtil.isEmpty(request.getTags())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签来源需提供标签列表");
        }
        if (sourceTypeEnum == SprintPlanSourceTypeEnum.MOCK_INTERVIEW && request.getSourceId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "模拟面试来源需指定面试记录 id");
        }
    }

    @Override
    public InterviewSprintPlan getValidPlan(Long planId, Long userId) {
        if (planId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        InterviewSprintPlan plan = this.getById(planId);
        if (plan == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "计划不存在");
        }
        if (!plan.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问该计划");
        }
        return plan;
    }

    // ======================= 重复保护 ========================

    /**
     * 同一用户同一来源类型同一来源 id 只能有一个"进行中"的计划
     */
    private void checkDuplicate(Long userId, int sourceType, Long sourceId) {
        LambdaQueryWrapper<InterviewSprintPlan> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(InterviewSprintPlan::getUserId, userId)
                .eq(InterviewSprintPlan::getSourceType, sourceType)
                .eq(InterviewSprintPlan::getStatus, SprintPlanStatusEnum.IN_PROGRESS.getValue());
        if (sourceId != null) {
            queryWrapper.eq(InterviewSprintPlan::getSourceId, sourceId);
        } else {
            queryWrapper.isNull(InterviewSprintPlan::getSourceId);
        }
        if (this.count(queryWrapper) > 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "已存在进行中的同类计划，请先完成或废弃后再创建");
        }
    }

    // ======================= 创建计划 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSprintPlan(InterviewSprintPlanCreateRequest request, HttpServletRequest httpRequest) {
        // 1. 参数校验
        validCreateRequest(request);
        User loginUser = userService.getLoginUser(httpRequest);
        Long userId = loginUser.getId();

        int sourceType = request.getSourceType();
        SprintPlanSourceTypeEnum sourceTypeEnum = SprintPlanSourceTypeEnum.getEnumByValue(sourceType);

        // 2. 重复检查
        checkDuplicate(userId, sourceType, request.getSourceId());

        // 3. 收集素材
        List<Question> questions = gatherQuestions(sourceTypeEnum, request, userId);
        List<Post> posts = gatherPosts(sourceTypeEnum, request, userId);
        String interviewSummary = gatherInterviewSummary(sourceTypeEnum, request, userId);

        if (CollUtil.isEmpty(questions) && CollUtil.isEmpty(posts) && StrUtil.isBlank(interviewSummary)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "没有足够的内容生成计划，请检查来源数据");
        }

        // 4. 计算日期
        int duration = request.getDuration();
        LocalDate startDate;
        if (StrUtil.isNotBlank(request.getStartDate())) {
            startDate = LocalDate.parse(request.getStartDate());
        } else {
            startDate = LocalDate.now();
        }
        LocalDate endDate = startDate.plusDays(duration - 1);

        // 5. 生成每日计划（AI + 规则兜底）
        List<DailyPlanItem> dailyItems = generateDailyPlan(
                duration, questions, posts, interviewSummary, sourceTypeEnum, request.getTags());

        // 6. 持久化计划
        InterviewSprintPlan plan = new InterviewSprintPlan();
        plan.setUserId(userId);
        plan.setPlanName(buildPlanName(sourceTypeEnum, request));
        plan.setDuration(duration);
        plan.setSourceType(sourceType);
        plan.setSourceId(request.getSourceId());
        plan.setStartDate(java.sql.Date.valueOf(startDate));
        plan.setEndDate(java.sql.Date.valueOf(endDate));
        int totalQ = 0;
        for (DailyPlanItem item : dailyItems) {
            if (CollUtil.isNotEmpty(item.getQuestionIds())) {
                totalQ += item.getQuestionIds().size();
            }
        }
        plan.setTotalQuestions(totalQ);
        plan.setCompletedCount(0);
        plan.setStatus(SprintPlanStatusEnum.IN_PROGRESS.getValue());
        this.save(plan);

        // 7. 持久化每日任务
        List<InterviewSprintPlanTask> tasks = new ArrayList<>();
        for (int i = 0; i < dailyItems.size(); i++) {
            DailyPlanItem item = dailyItems.get(i);
            InterviewSprintPlanTask task = new InterviewSprintPlanTask();
            task.setPlanId(plan.getId());
            task.setUserId(userId);
            task.setDayNumber(i + 1);
            task.setTaskDate(java.sql.Date.valueOf(startDate.plusDays(i)));
            task.setQuestionIds(CollUtil.isNotEmpty(item.getQuestionIds())
                    ? JSONUtil.toJsonStr(item.getQuestionIds()) : null);
            task.setPostIds(CollUtil.isNotEmpty(item.getPostIds())
                    ? JSONUtil.toJsonStr(item.getPostIds()) : null);
            task.setMockInterviewGoal(item.getMockInterviewGoal());
            task.setStudyNotes(item.getStudyNotes());
            task.setStatus(SprintPlanTaskStatusEnum.PENDING.getValue());
            tasks.add(task);
        }
        planTaskService.batchCreateTasks(tasks);

        return plan.getId();
    }

    // ======================= 素材收集 ========================

    /**
     * 根据来源类型收集题目
     */
    private List<Question> gatherQuestions(SprintPlanSourceTypeEnum sourceType,
                                           InterviewSprintPlanCreateRequest request, Long userId) {
        switch (sourceType) {
            case QUESTION_BANK: {
                // 通过题库-题目关联表查询
                LambdaQueryWrapper<QuestionBankQuestion> bqQueryWrapper = new LambdaQueryWrapper<>();
                bqQueryWrapper.eq(QuestionBankQuestion::getQuestionBankId, request.getSourceId());
                List<QuestionBankQuestion> relations = questionBankQuestionService.list(bqQueryWrapper);
                if (CollUtil.isEmpty(relations)) {
                    return Collections.emptyList();
                }
                List<Long> qIds = relations.stream()
                        .map(QuestionBankQuestion::getQuestionId)
                        .collect(Collectors.toList());
                return questionService.listByIds(qIds);
            }
            case TAG: {
                // 按标签模糊查询（复用 QuestionService 的 tags LIKE 模式）
                List<String> tags = request.getTags();
                LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.and(w -> {
                    for (int i = 0; i < tags.size(); i++) {
                        if (i == 0) {
                            w.like(Question::getTags, tags.get(i));
                        } else {
                            w.or().like(Question::getTags, tags.get(i));
                        }
                    }
                });
                // 限制数量避免拉取过多
                queryWrapper.last("LIMIT 500");
                return questionService.list(queryWrapper);
            }
            case FAVOURITED_POST: {
                // 收藏帖子来源不产生题目
                return Collections.emptyList();
            }
            case MOCK_INTERVIEW: {
                // 从模拟面试记录中提取相关题目
                if (request.getSourceId() == null) {
                    return Collections.emptyList();
                }
                MockInterview mi = mockInterviewService.getById(request.getSourceId());
                if (mi == null || !mi.getUserId().equals(userId)) {
                    return Collections.emptyList();
                }
                return extractQuestionsFromMockInterview(mi);
            }
            default:
                return Collections.emptyList();
        }
    }

    /**
     * 根据来源类型收集帖子
     */
    private List<Post> gatherPosts(SprintPlanSourceTypeEnum sourceType,
                                   InterviewSprintPlanCreateRequest request, Long userId) {
        if (sourceType == SprintPlanSourceTypeEnum.FAVOURITED_POST) {
            // 使用 PostFavourService 获取用户收藏帖子
            QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("isDelete", 0);
            Page<Post> favourPage = postFavourService.listFavourPostByPage(
                    new Page<>(1, 100), queryWrapper, userId);
            if (favourPage != null && CollUtil.isNotEmpty(favourPage.getRecords())) {
                return favourPage.getRecords();
            }
        }
        return Collections.emptyList();
    }

    /**
     * 根据来源类型收集面试摘要
     */
    private String gatherInterviewSummary(SprintPlanSourceTypeEnum sourceType,
                                          InterviewSprintPlanCreateRequest request, Long userId) {
        if (sourceType != SprintPlanSourceTypeEnum.MOCK_INTERVIEW) {
            return null;
        }
        if (request.getSourceId() == null) {
            return null;
        }
        MockInterview mi = mockInterviewService.getById(request.getSourceId());
        if (mi == null || !mi.getUserId().equals(userId)) {
            return null;
        }
        return summarizeMockInterview(mi);
    }

    /**
     * 从模拟面试消息中提取相关题目
     */
    private List<Question> extractQuestionsFromMockInterview(MockInterview mi) {
        if (StrUtil.isBlank(mi.getMessages())) {
            return Collections.emptyList();
        }
        // 尝试从面试消息中提取提到的题目关键词，搜索匹配的题目
        try {
            JSONArray messages = JSONUtil.parseArray(mi.getMessages());
            StringBuilder contentBuilder = new StringBuilder();
            for (int i = 0; i < messages.size(); i++) {
                JSONObject msg = messages.getJSONObject(i);
                String message = msg.getStr("message", "");
                contentBuilder.append(message).append(" ");
            }
            String content = contentBuilder.toString();
            if (StrUtil.isBlank(content)) {
                return Collections.emptyList();
            }
            // 使用面试岗位作为搜索关键词
            String searchText = mi.getJobPosition();
            if (StrUtil.isBlank(searchText)) {
                searchText = "面试";
            }
            LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.like(Question::getTitle, searchText)
                    .or()
                    .like(Question::getTags, searchText);
            queryWrapper.last("LIMIT 200");
            return questionService.list(queryWrapper);
        } catch (Exception e) {
            log.warn("解析模拟面试消息失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 摘要化模拟面试记录
     */
    private String summarizeMockInterview(MockInterview mi) {
        StringBuilder summary = new StringBuilder();
        summary.append("面试岗位: ").append(StrUtil.blankToDefault(mi.getJobPosition(), "未知"));
        summary.append(", 工作年限: ").append(StrUtil.blankToDefault(mi.getWorkExperience(), "未知"));
        summary.append(", 难度: ").append(StrUtil.blankToDefault(mi.getDifficulty(), "未知"));
        // 从消息中提取摘要
        if (StrUtil.isNotBlank(mi.getMessages())) {
            try {
                JSONArray messages = JSONUtil.parseArray(mi.getMessages());
                summary.append(", 对话轮次: ").append(messages.size());
                // 提取最后一条消息（通常是总结）
                if (messages.size() > 0) {
                    JSONObject lastMsg = messages.getJSONObject(messages.size() - 1);
                    String lastMessage = lastMsg.getStr("message", "");
                    if (lastMessage.length() > 200) {
                        lastMessage = lastMessage.substring(0, 200) + "...";
                    }
                    summary.append(", 最后总结: ").append(lastMessage);
                }
            } catch (Exception e) {
                log.warn("解析面试消息失败: {}", e.getMessage());
            }
        }
        return summary.toString();
    }

    // ======================= 计划生成（AI + 规则兜底）========================

    /**
     * 生成每日计划，AI 优先，失败则规则兜底
     */
    private List<DailyPlanItem> generateDailyPlan(int duration, List<Question> questions,
                                                   List<Post> posts, String interviewSummary,
                                                   SprintPlanSourceTypeEnum sourceType,
                                                   List<String> tags) {
        try {
            return generateByAI(duration, questions, posts, interviewSummary, sourceType, tags);
        } catch (Exception e) {
            log.warn("AI 生成计划失败，使用规则兜底: {}", e.getMessage());
            return generateByRule(duration, questions, posts, interviewSummary, sourceType);
        }
    }

    /**
     * AI 生成计划
     */
    private List<DailyPlanItem> generateByAI(int duration, List<Question> questions,
                                              List<Post> posts, String interviewSummary,
                                              SprintPlanSourceTypeEnum sourceType,
                                              List<String> tags) {
        // 构建题目摘要（控制 prompt 长度）
        StringBuilder questionSummary = new StringBuilder();
        int qLimit = Math.min(CollUtil.isEmpty(questions) ? 0 : questions.size(), 200);
        for (int i = 0; i < qLimit; i++) {
            Question q = questions.get(i);
            questionSummary.append(String.format("题目%d(id=%d): %s [标签: %s]\n",
                    i + 1, q.getId(), q.getTitle(), q.getTags()));
        }

        // 构建帖子摘要
        StringBuilder postSummary = new StringBuilder();
        int pLimit = Math.min(CollUtil.isEmpty(posts) ? 0 : posts.size(), 50);
        for (int i = 0; i < pLimit; i++) {
            Post p = posts.get(i);
            postSummary.append(String.format("帖子%d(id=%d): %s\n",
                    i + 1, p.getId(), p.getTitle()));
        }

        String systemPrompt = "你是一个专业的面试辅导教练。你需要根据用户提供的学习材料，"
                + "生成一个结构化的面试冲刺计划。\n"
                + "要求：\n"
                + "1. 将题目均匀分配到每一天，每天的题目数量大致相同\n"
                + "2. 按照难度递进安排：前期基础题，后期进阶题\n"
                + "3. 每天安排 2-3 篇相关帖子作为补充阅读（如果有帖子可用）\n"
                + "4. 为每天设定一个具体可衡量的模拟面试目标\n"
                + "5. 输出严格的 JSON 数组格式，不要包含其他文字\n";

        String userPrompt = String.format(
                "请生成一个 %d 天的面试冲刺计划。\n\n"
                        + "来源类型：%s\n"
                        + "标签方向：%s\n\n"
                        + "可用题目列表（共 %d 道）：\n%s\n\n"
                        + "可用帖子列表（共 %d 篇）：\n%s\n\n"
                        + "模拟面试历史摘要：\n%s\n\n"
                        + "请输出 JSON 数组，每个元素代表一天，格式如下：\n"
                        + "[\n"
                        + "  {\n"
                        + "    \"day\": 1,\n"
                        + "    \"questionIds\": [题目id列表],\n"
                        + "    \"postIds\": [帖子id列表],\n"
                        + "    \"mockInterviewGoal\": \"今天的模拟面试目标\",\n"
                        + "    \"studyNotes\": \"今天的学习要点和建议\"\n"
                        + "  }\n"
                        + "]\n"
                        + "只输出 JSON，不要其他文字。",
                duration,
                sourceType.getText(),
                CollUtil.isNotEmpty(tags) ? String.join(", ", tags) : "无",
                CollUtil.isEmpty(questions) ? 0 : questions.size(),
                questionSummary.toString(),
                CollUtil.isEmpty(posts) ? 0 : posts.size(),
                postSummary.toString(),
                StrUtil.isNotBlank(interviewSummary) ? interviewSummary : "无历史面试记录"
        );

        String aiResponse = aiManager.doChat(systemPrompt, userPrompt);
        return parseAndValidateAIResponse(aiResponse, duration, questions, posts);
    }

    /**
     * 解析并校验 AI 返回结果
     */
    private List<DailyPlanItem> parseAndValidateAIResponse(String response, int duration,
                                                            List<Question> questions, List<Post> posts) {
        // 去除 markdown 代码围栏
        String json = response.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```json?", "").replaceAll("```$", "").trim();
        }

        JSONArray arr = JSONUtil.parseArray(json);

        // 构建合法的 id 集合
        Set<Long> validQIds = CollUtil.isEmpty(questions)
                ? Collections.emptySet()
                : questions.stream().map(Question::getId).collect(Collectors.toSet());
        Set<Long> validPIds = CollUtil.isEmpty(posts)
                ? Collections.emptySet()
                : posts.stream().map(Post::getId).collect(Collectors.toSet());

        List<DailyPlanItem> items = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            DailyPlanItem item = new DailyPlanItem();

            // 过滤合法的题目 id
            JSONArray qArr = obj.getJSONArray("questionIds");
            List<Long> qIds = new ArrayList<>();
            if (qArr != null) {
                for (int j = 0; j < qArr.size(); j++) {
                    Long qId = qArr.getLong(j);
                    if (qId != null && validQIds.contains(qId)) {
                        qIds.add(qId);
                    }
                }
            }
            item.setQuestionIds(qIds);

            // 过滤合法的帖子 id
            JSONArray pArr = obj.getJSONArray("postIds");
            List<Long> pIds = new ArrayList<>();
            if (pArr != null) {
                for (int j = 0; j < pArr.size(); j++) {
                    Long pId = pArr.getLong(j);
                    if (pId != null && validPIds.contains(pId)) {
                        pIds.add(pId);
                    }
                }
            }
            item.setPostIds(pIds);

            item.setMockInterviewGoal(obj.getStr("mockInterviewGoal", ""));
            item.setStudyNotes(obj.getStr("studyNotes", ""));
            items.add(item);
        }

        // 校验天数：不足则用空项补齐，多余则截断
        while (items.size() < duration) {
            DailyPlanItem emptyItem = new DailyPlanItem();
            emptyItem.setQuestionIds(Collections.emptyList());
            emptyItem.setPostIds(Collections.emptyList());
            emptyItem.setMockInterviewGoal("补充练习");
            emptyItem.setStudyNotes("请复习之前的内容");
            items.add(emptyItem);
        }
        if (items.size() > duration) {
            items = items.subList(0, duration);
        }

        return items;
    }

    // ======================= 规则兜底 ========================

    /**
     * 规则兜底生成计划
     */
    private List<DailyPlanItem> generateByRule(int duration, List<Question> questions,
                                                List<Post> posts, String interviewSummary,
                                                SprintPlanSourceTypeEnum sourceType) {
        List<DailyPlanItem> items = new ArrayList<>();

        // 打乱以增加多样性
        List<Question> shuffledQ = new ArrayList<>(CollUtil.isEmpty(questions) ? Collections.emptyList() : questions);
        Collections.shuffle(shuffledQ);
        List<Post> shuffledP = new ArrayList<>(CollUtil.isEmpty(posts) ? Collections.emptyList() : posts);
        Collections.shuffle(shuffledP);

        int qPerDay = shuffledQ.isEmpty() ? 0 : Math.max(1, shuffledQ.size() / duration);
        int pPerDay = shuffledP.isEmpty() ? 0 : Math.max(1, shuffledP.size() / duration);

        int qIdx = 0;
        int pIdx = 0;

        for (int day = 1; day <= duration; day++) {
            DailyPlanItem item = new DailyPlanItem();

            // 分配题目
            List<Long> dayQIds = new ArrayList<>();
            if (!shuffledQ.isEmpty()) {
                int qCount = (day == duration) ? (shuffledQ.size() - qIdx) : qPerDay;
                for (int j = 0; j < qCount && qIdx < shuffledQ.size(); j++) {
                    dayQIds.add(shuffledQ.get(qIdx++).getId());
                }
            }
            item.setQuestionIds(dayQIds);

            // 分配帖子
            List<Long> dayPIds = new ArrayList<>();
            if (!shuffledP.isEmpty()) {
                int pCount = (day == duration) ? (shuffledP.size() - pIdx) : pPerDay;
                for (int j = 0; j < pCount && pIdx < shuffledP.size(); j++) {
                    dayPIds.add(shuffledP.get(pIdx++).getId());
                }
            }
            item.setPostIds(dayPIds);

            // 生成模拟面试目标
            String goal = buildRuleBasedGoal(day, duration, dayQIds, shuffledQ);
            item.setMockInterviewGoal(goal);
            item.setStudyNotes(String.format("第 %d/%d 天，今天需要完成 %d 道题目和 %d 篇阅读。坚持就是胜利！",
                    day, duration, dayQIds.size(), dayPIds.size()));

            items.add(item);
        }

        return items;
    }

    /**
     * 根据当天题目的标签生成模拟面试目标
     */
    private String buildRuleBasedGoal(int day, int duration, List<Long> dayQIds, List<Question> allQuestions) {
        Set<String> todayTags = new HashSet<>();
        Map<Long, Question> qMap = allQuestions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q, (a, b) -> a));
        for (Long qId : dayQIds) {
            Question q = qMap.get(qId);
            if (q != null && StrUtil.isNotBlank(q.getTags())) {
                try {
                    List<String> tags = JSONUtil.toList(JSONUtil.parseArray(q.getTags()), String.class);
                    todayTags.addAll(tags);
                } catch (Exception ignored) {
                    // 忽略解析错误
                }
            }
        }
        String tagStr = todayTags.isEmpty() ? "综合" : String.join("、", todayTags);
        return String.format("第 %d/%d 天模拟面试：重点练习【%s】方向，目标完成 %d 道题目的模拟回答",
                day, duration, tagStr, dayQIds.size());
    }

    /**
     * 构建计划名称
     */
    private String buildPlanName(SprintPlanSourceTypeEnum sourceType, InterviewSprintPlanCreateRequest request) {
        String name = sourceType.getText() + "冲刺计划";
        if (sourceType == SprintPlanSourceTypeEnum.TAG && CollUtil.isNotEmpty(request.getTags())) {
            name = String.join("、", request.getTags()) + " " + name;
        }
        return name;
    }

    // ======================= 获取今日任务 ========================

    @Override
    public InterviewSprintPlanTaskVO getTodayTask(Long planId, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        getValidPlan(planId, loginUser.getId());

        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<InterviewSprintPlanTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(InterviewSprintPlanTask::getPlanId, planId)
                .eq(InterviewSprintPlanTask::getUserId, loginUser.getId())
                .eq(InterviewSprintPlanTask::getTaskDate, java.sql.Date.valueOf(today));

        InterviewSprintPlanTask task = planTaskService.getOne(queryWrapper);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "今天没有计划任务");
        }
        return planTaskService.enrichTaskVO(task);
    }

    // ======================= 标记完成 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(InterviewSprintPlanTaskCompleteRequest request, HttpServletRequest httpRequest) {
        if (request == null || request.getTaskId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(httpRequest);
        InterviewSprintPlanTask task = planTaskService.getById(request.getTaskId());
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        }
        if (!task.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作该任务");
        }
        // 幂等处理：已完成则直接返回
        if (task.getStatus() == SprintPlanTaskStatusEnum.COMPLETED.getValue()) {
            return;
        }
        // 标记任务完成
        task.setStatus(SprintPlanTaskStatusEnum.COMPLETED.getValue());
        task.setCompletedTime(new Date());
        planTaskService.updateById(task);

        // 递增计划已完成天数
        InterviewSprintPlan plan = this.getById(task.getPlanId());
        if (plan != null) {
            plan.setCompletedCount(plan.getCompletedCount() + 1);
            if (plan.getCompletedCount() >= plan.getDuration()) {
                plan.setStatus(SprintPlanStatusEnum.COMPLETED.getValue());
            }
            this.updateById(plan);
        }
    }

    // ======================= 重新生成弱项任务 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void regenerateWeakTagTasks(InterviewSprintPlanRegenerateRequest request,
                                        HttpServletRequest httpRequest) {
        if (request == null || request.getPlanId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(httpRequest);
        InterviewSprintPlan plan = getValidPlan(request.getPlanId(), loginUser.getId());

        // 确定需要重新生成的天数
        List<Integer> dayNumbers = request.getDayNumbers();
        if (CollUtil.isEmpty(dayNumbers)) {
            dayNumbers = identifyWeakDays(plan.getId(), loginUser.getId());
        }
        if (CollUtil.isEmpty(dayNumbers)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "没有需要重新生成的弱项天");
        }

        // 获取计划下所有任务，收集已使用的题目 id
        LambdaQueryWrapper<InterviewSprintPlanTask> allTasksQw = new LambdaQueryWrapper<>();
        allTasksQw.eq(InterviewSprintPlanTask::getPlanId, plan.getId());
        List<InterviewSprintPlanTask> allTasks = planTaskService.list(allTasksQw);
        Set<Long> usedQIds = new HashSet<>();
        for (InterviewSprintPlanTask t : allTasks) {
            if (StrUtil.isNotBlank(t.getQuestionIds())) {
                try {
                    List<Long> ids = JSONUtil.toList(JSONUtil.parseArray(t.getQuestionIds()), Long.class);
                    usedQIds.addAll(ids);
                } catch (Exception ignored) {
                }
            }
        }

        // 获取需要重新生成的任务
        LambdaQueryWrapper<InterviewSprintPlanTask> targetQw = new LambdaQueryWrapper<>();
        targetQw.eq(InterviewSprintPlanTask::getPlanId, plan.getId())
                .in(InterviewSprintPlanTask::getDayNumber, dayNumbers);
        List<InterviewSprintPlanTask> existingTasks = planTaskService.list(targetQw);

        // 根据原来源收集新题目（排除已使用的）
        InterviewSprintPlanCreateRequest fakeRequest = new InterviewSprintPlanCreateRequest();
        fakeRequest.setSourceType(plan.getSourceType());
        fakeRequest.setSourceId(plan.getSourceId());
        SprintPlanSourceTypeEnum sourceTypeEnum = SprintPlanSourceTypeEnum.getEnumByValue(plan.getSourceType());
        List<Question> allQuestions = gatherQuestions(sourceTypeEnum, fakeRequest, loginUser.getId());
        List<Question> newQuestions = allQuestions.stream()
                .filter(q -> !usedQIds.contains(q.getId()))
                .collect(Collectors.toList());

        List<Post> allPosts = gatherPosts(sourceTypeEnum, fakeRequest, loginUser.getId());
        List<Post> newPosts = allPosts.stream()
                .filter(p -> {
                    // 收集已使用的帖子 id
                    Set<Long> usedPIds = new HashSet<>();
                    for (InterviewSprintPlanTask t : allTasks) {
                        if (StrUtil.isNotBlank(t.getPostIds())) {
                            try {
                                List<Long> ids = JSONUtil.toList(JSONUtil.parseArray(t.getPostIds()), Long.class);
                                usedPIds.addAll(ids);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    return !usedPIds.contains(p.getId());
                })
                .collect(Collectors.toList());

        // 对每一天重新生成
        for (InterviewSprintPlanTask task : existingTasks) {
            // 使用规则兜底为每天分配新题目
            int daysToRegenerate = existingTasks.size();
            List<DailyPlanItem> newItems = generateByRule(daysToRegenerate, newQuestions, newPosts,
                    null, sourceTypeEnum);

            int itemIdx = existingTasks.indexOf(task);
            if (itemIdx < newItems.size()) {
                DailyPlanItem newItem = newItems.get(itemIdx);
                task.setQuestionIds(CollUtil.isNotEmpty(newItem.getQuestionIds())
                        ? JSONUtil.toJsonStr(newItem.getQuestionIds()) : null);
                task.setPostIds(CollUtil.isNotEmpty(newItem.getPostIds())
                        ? JSONUtil.toJsonStr(newItem.getPostIds()) : null);
                task.setMockInterviewGoal(newItem.getMockInterviewGoal());
                task.setStudyNotes(newItem.getStudyNotes());
            }
            // 重置状态
            task.setStatus(SprintPlanTaskStatusEnum.PENDING.getValue());
            task.setCompletedTime(null);
            planTaskService.updateById(task);

            // 从 newQuestions 中移除已分配的，避免重复分配
            if (itemIdx < newItems.size()) {
                Set<Long> assignedIds = new HashSet<>(newItems.get(itemIdx).getQuestionIds());
                newQuestions = newQuestions.stream()
                        .filter(q -> !assignedIds.contains(q.getId()))
                        .collect(Collectors.toList());
            }
        }
    }

    /**
     * 识别弱项天：未完成的天
     */
    private List<Integer> identifyWeakDays(Long planId, Long userId) {
        LambdaQueryWrapper<InterviewSprintPlanTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(InterviewSprintPlanTask::getPlanId, planId)
                .eq(InterviewSprintPlanTask::getUserId, userId)
                .eq(InterviewSprintPlanTask::getStatus, SprintPlanTaskStatusEnum.PENDING.getValue())
                .orderByAsc(InterviewSprintPlanTask::getDayNumber);
        List<InterviewSprintPlanTask> tasks = planTaskService.list(queryWrapper);
        return tasks.stream()
                .map(InterviewSprintPlanTask::getDayNumber)
                .collect(Collectors.toList());
    }

    // ======================= 查询计划详情 ========================

    @Override
    public InterviewSprintPlanVO getSprintPlanDetail(Long planId, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        InterviewSprintPlan plan = getValidPlan(planId, loginUser.getId());
        InterviewSprintPlanVO vo = InterviewSprintPlanVO.objToVo(plan);
        vo.setTasks(planTaskService.listTasksByPlanId(planId, loginUser.getId()));
        return vo;
    }

    // ======================= 分页查询 ========================

    @Override
    public Page<InterviewSprintPlanVO> listSprintPlanByPage(InterviewSprintPlanQueryRequest request,
                                                             HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        long current = request.getCurrent();
        long size = request.getPageSize();

        LambdaQueryWrapper<InterviewSprintPlan> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(InterviewSprintPlan::getUserId, loginUser.getId());
        if (request.getId() != null) {
            queryWrapper.eq(InterviewSprintPlan::getId, request.getId());
        }
        if (request.getStatus() != null) {
            queryWrapper.eq(InterviewSprintPlan::getStatus, request.getStatus());
        }
        if (request.getSourceType() != null) {
            queryWrapper.eq(InterviewSprintPlan::getSourceType, request.getSourceType());
        }
        // 排序：默认按创建时间降序
        queryWrapper.orderByDesc(InterviewSprintPlan::getCreateTime);

        Page<InterviewSprintPlan> planPage = this.page(new Page<>(current, size), queryWrapper);

        // 转换为 VO 分页
        Page<InterviewSprintPlanVO> voPage = new Page<>(current, size, planPage.getTotal());
        List<InterviewSprintPlanVO> voList = planPage.getRecords().stream()
                .map(InterviewSprintPlanVO::objToVo)
                .collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }
}
