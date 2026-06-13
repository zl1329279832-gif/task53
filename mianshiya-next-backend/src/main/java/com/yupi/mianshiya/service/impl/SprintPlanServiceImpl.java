package com.yupi.mianshiya.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.constant.CommonConstant;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.exception.ThrowUtils;
import com.yupi.mianshiya.manager.AiManager;
import com.yupi.mianshiya.mapper.SprintPlanDailyTaskMapper;
import com.yupi.mianshiya.mapper.SprintPlanMapper;
import com.yupi.mianshiya.model.dto.sprintplan.SprintPlanAddRequest;
import com.yupi.mianshiya.model.dto.sprintplan.SprintPlanCompleteRequest;
import com.yupi.mianshiya.model.dto.sprintplan.SprintPlanQueryRequest;
import com.yupi.mianshiya.model.dto.sprintplan.SprintPlanRegenerateRequest;
import com.yupi.mianshiya.model.entity.MockInterview;
import com.yupi.mianshiya.model.entity.Post;
import com.yupi.mianshiya.model.entity.PostFavour;
import com.yupi.mianshiya.model.entity.Question;
import com.yupi.mianshiya.model.entity.QuestionBankQuestion;
import com.yupi.mianshiya.model.entity.SprintPlan;
import com.yupi.mianshiya.model.entity.SprintPlanDailyTask;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.model.enums.MockInterviewStatusEnum;
import com.yupi.mianshiya.model.enums.SprintPlanStatusEnum;
import com.yupi.mianshiya.model.vo.PostVO;
import com.yupi.mianshiya.model.vo.QuestionVO;
import com.yupi.mianshiya.model.vo.SprintPlanDailyTaskVO;
import com.yupi.mianshiya.model.vo.SprintPlanProgressVO;
import com.yupi.mianshiya.model.vo.SprintPlanVO;
import com.yupi.mianshiya.model.vo.UserVO;
import com.yupi.mianshiya.service.MockInterviewService;
import com.yupi.mianshiya.service.PostFavourService;
import com.yupi.mianshiya.service.PostService;
import com.yupi.mianshiya.service.QuestionBankQuestionService;
import com.yupi.mianshiya.service.QuestionService;
import com.yupi.mianshiya.service.SprintPlanService;
import com.yupi.mianshiya.service.UserService;
import com.yupi.mianshiya.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 面试冲刺计划服务实现
 */
@Service
@Slf4j
public class SprintPlanServiceImpl extends ServiceImpl<SprintPlanMapper, SprintPlan>
        implements SprintPlanService {

    @Resource
    private SprintPlanDailyTaskMapper sprintPlanDailyTaskMapper;

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    private PostFavourService postFavourService;

    @Resource
    private PostService postService;

    @Resource
    private MockInterviewService mockInterviewService;

    @Resource
    private UserService userService;

    @Resource
    private AiManager aiManager;

    /**
     * AI 分配计划的系统提示词
     */
    private static final String PLAN_GENERATION_SYSTEM_PROMPT =
            "你是一位面试辅导专家，我需要你帮我制定一个面试冲刺计划。\n" +
            "我会给你一组面试题目和帖子的信息，请帮我分配到每天的学习任务中。\n" +
            "要求：\n" +
            "1. 每天分配的题目数量应大致均匀\n" +
            "2. 相同标签/主题的题目应该安排在同一天或相邻的几天内集中学习\n" +
            "3. 难度应该从基础到进阶逐步递增\n" +
            "4. 每天需要给出一个简短的模拟面试目标描述（一句话）\n" +
            "5. 推荐帖子应该和当天的题目主题相关\n\n" +
            "请严格按照以下 JSON 格式输出，不要输出任何多余的内容：\n" +
            "[{\"dayNumber\":1,\"questionIds\":[1,2,3],\"postIds\":[10,11],\"mockGoal\":\"基础Java语法和面向对象\"}, ...]\n";

    // region 核心业务方法

    /**
     * 创建冲刺计划
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSprintPlan(SprintPlanAddRequest addRequest, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(addRequest == null || loginUser == null, ErrorCode.PARAMS_ERROR);
        String title = addRequest.getTitle();
        Integer totalDays = addRequest.getTotalDays();
        List<Long> questionBankIds = addRequest.getQuestionBankIds();
        List<String> tags = addRequest.getTags();
        Boolean includeFavouritePosts = addRequest.getIncludeFavouritePosts();
        Boolean includeMockInterviews = addRequest.getIncludeMockInterviews();

        ThrowUtils.throwIf(StrUtil.isBlank(title), ErrorCode.PARAMS_ERROR, "标题不能为空");
        ThrowUtils.throwIf(title.length() > 256, ErrorCode.PARAMS_ERROR, "标题过长");
        ThrowUtils.throwIf(totalDays == null || (totalDays != 7 && totalDays != 14),
                ErrorCode.PARAMS_ERROR, "计划天数必须为7或14");

        // 至少选择一个来源
        boolean hasSource = CollUtil.isNotEmpty(questionBankIds)
                || CollUtil.isNotEmpty(tags)
                || Boolean.TRUE.equals(includeFavouritePosts)
                || Boolean.TRUE.equals(includeMockInterviews);
        ThrowUtils.throwIf(!hasSource, ErrorCode.PARAMS_ERROR, "至少选择一个题目来源");

        // 2. 重复计划检查
        QueryWrapper<SprintPlan> dupWrapper = new QueryWrapper<>();
        dupWrapper.eq("userId", loginUser.getId());
        dupWrapper.eq("status", SprintPlanStatusEnum.IN_PROGRESS.getValue());
        long activeCount = this.count(dupWrapper);
        ThrowUtils.throwIf(activeCount > 0, ErrorCode.OPERATION_ERROR, "已有进行中的冲刺计划，请先完成或放弃当前计划");

        // 3. 收集题目和帖子
        List<Question> questions = gatherQuestions(questionBankIds, tags, includeFavouritePosts,
                includeMockInterviews, loginUser);
        ThrowUtils.throwIf(CollUtil.isEmpty(questions), ErrorCode.OPERATION_ERROR, "未找到可用的题目，请调整来源条件");
        List<Post> posts = gatherPosts(includeFavouritePosts, tags, loginUser);

        // 4. 分配到每日任务
        Date startDate = DateUtil.beginOfDay(new Date());
        Calendar cal = Calendar.getInstance();
        cal.setTime(startDate);
        cal.add(Calendar.DAY_OF_MONTH, totalDays - 1);
        Date endDate = cal.getTime();

        SprintPlan sprintPlan = new SprintPlan();
        sprintPlan.setTitle(title);
        sprintPlan.setTotalDays(totalDays);
        sprintPlan.setQuestionBankIds(CollUtil.isNotEmpty(questionBankIds) ? JSONUtil.toJsonStr(questionBankIds) : null);
        sprintPlan.setTags(CollUtil.isNotEmpty(tags) ? JSONUtil.toJsonStr(tags) : null);
        sprintPlan.setStatus(SprintPlanStatusEnum.IN_PROGRESS.getValue());
        sprintPlan.setUserId(loginUser.getId());
        sprintPlan.setStartDate(startDate);
        sprintPlan.setEndDate(endDate);

        // 保存计划
        boolean result = this.save(sprintPlan);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建计划失败");

        // 5. 生成每日任务并保存
        List<SprintPlanDailyTask> dailyTasks = distributeQuestions(sprintPlan, questions, posts, null);
        for (SprintPlanDailyTask task : dailyTasks) {
            task.setSprintPlanId(sprintPlan.getId());
            task.setUserId(loginUser.getId());
            sprintPlanDailyTaskMapper.insert(task);
        }

        return sprintPlan.getId();
    }

    /**
     * 获取今日任务
     */
    @Override
    public SprintPlanDailyTaskVO getTodayTask(Long sprintPlanId, User loginUser, HttpServletRequest request) {
        // 1. 查询计划
        SprintPlan plan = this.getById(sprintPlanId);
        ThrowUtils.throwIf(plan == null, ErrorCode.NOT_FOUND_ERROR, "计划不存在");
        ThrowUtils.throwIf(!plan.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);

        // 2. 计算今天是第几天
        int dayNumber = calcDayNumber(plan.getStartDate());
        ThrowUtils.throwIf(dayNumber < 1 || dayNumber > plan.getTotalDays(),
                ErrorCode.PARAMS_ERROR, "当前日期不在计划范围内");

        // 3. 查询每日任务
        QueryWrapper<SprintPlanDailyTask> taskWrapper = new QueryWrapper<>();
        taskWrapper.eq("sprintPlanId", sprintPlanId);
        taskWrapper.eq("dayNumber", dayNumber);
        SprintPlanDailyTask dailyTask = sprintPlanDailyTaskMapper.selectOne(taskWrapper);
        ThrowUtils.throwIf(dailyTask == null, ErrorCode.NOT_FOUND_ERROR, "今日任务不存在");

        // 4. 转换并填充详情
        return enrichDailyTaskVO(dailyTask);
    }

    /**
     * 完成任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean completeTask(SprintPlanCompleteRequest completeRequest, User loginUser) {
        ThrowUtils.throwIf(completeRequest == null, ErrorCode.PARAMS_ERROR);
        Long sprintPlanId = completeRequest.getSprintPlanId();
        Integer dayNumber = completeRequest.getDayNumber();
        ThrowUtils.throwIf(sprintPlanId == null || sprintPlanId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(dayNumber == null || dayNumber <= 0, ErrorCode.PARAMS_ERROR);

        // 1. 校验计划
        SprintPlan plan = this.getById(sprintPlanId);
        ThrowUtils.throwIf(plan == null, ErrorCode.NOT_FOUND_ERROR, "计划不存在");
        ThrowUtils.throwIf(!plan.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(plan.getStatus() != SprintPlanStatusEnum.IN_PROGRESS.getValue(),
                ErrorCode.OPERATION_ERROR, "计划已结束");

        // 2. 查询并更新任务
        QueryWrapper<SprintPlanDailyTask> taskWrapper = new QueryWrapper<>();
        taskWrapper.eq("sprintPlanId", sprintPlanId);
        taskWrapper.eq("dayNumber", dayNumber);
        SprintPlanDailyTask dailyTask = sprintPlanDailyTaskMapper.selectOne(taskWrapper);
        ThrowUtils.throwIf(dailyTask == null, ErrorCode.NOT_FOUND_ERROR, "任务不存在");
        ThrowUtils.throwIf(dailyTask.getIsCompleted() == 1, ErrorCode.OPERATION_ERROR, "该任务已完成");

        dailyTask.setIsCompleted(1);
        sprintPlanDailyTaskMapper.updateById(dailyTask);

        // 3. 检查是否全部完成，自动更新计划状态
        QueryWrapper<SprintPlanDailyTask> remainWrapper = new QueryWrapper<>();
        remainWrapper.eq("sprintPlanId", sprintPlanId);
        remainWrapper.eq("isCompleted", 0);
        Long remaining = sprintPlanDailyTaskMapper.selectCount(remainWrapper);
        if (remaining == 0) {
            SprintPlan planUpdate = new SprintPlan();
            planUpdate.setId(sprintPlanId);
            planUpdate.setStatus(SprintPlanStatusEnum.COMPLETED.getValue());
            this.updateById(planUpdate);
        }

        return true;
    }

    /**
     * 重新生成薄弱标签任务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean regenerateWeakTagTasks(SprintPlanRegenerateRequest regenerateRequest, User loginUser) {
        ThrowUtils.throwIf(regenerateRequest == null, ErrorCode.PARAMS_ERROR);
        Long sprintPlanId = regenerateRequest.getSprintPlanId();
        List<String> weakTags = regenerateRequest.getWeakTags();
        Integer fromDay = regenerateRequest.getFromDay();

        ThrowUtils.throwIf(sprintPlanId == null || sprintPlanId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(CollUtil.isEmpty(weakTags), ErrorCode.PARAMS_ERROR, "薄弱标签不能为空");
        ThrowUtils.throwIf(fromDay == null || fromDay < 1, ErrorCode.PARAMS_ERROR, "起始天数无效");

        // 1. 校验计划
        SprintPlan plan = this.getById(sprintPlanId);
        ThrowUtils.throwIf(plan == null, ErrorCode.NOT_FOUND_ERROR, "计划不存在");
        ThrowUtils.throwIf(!plan.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(plan.getStatus() != SprintPlanStatusEnum.IN_PROGRESS.getValue(),
                ErrorCode.OPERATION_ERROR, "计划已结束");
        ThrowUtils.throwIf(fromDay > plan.getTotalDays(), ErrorCode.PARAMS_ERROR, "起始天数超出计划范围");

        // 2. 收集已使用的题目 ID（已完成天数）
        QueryWrapper<SprintPlanDailyTask> completedWrapper = new QueryWrapper<>();
        completedWrapper.eq("sprintPlanId", sprintPlanId);
        completedWrapper.lt("dayNumber", fromDay);
        List<SprintPlanDailyTask> completedTasks = sprintPlanDailyTaskMapper.selectList(completedWrapper);
        Set<Long> usedQuestionIds = new HashSet<>();
        for (SprintPlanDailyTask t : completedTasks) {
            if (StrUtil.isNotBlank(t.getQuestionIds())) {
                usedQuestionIds.addAll(JSONUtil.toList(JSONUtil.parseArray(t.getQuestionIds()), Long.class));
            }
        }

        // 3. 根据薄弱标签获取新题目
        QueryWrapper<Question> questionWrapper = new QueryWrapper<>();
        for (String tag : weakTags) {
            questionWrapper.like("tags", "\"" + tag + "\"");
        }
        questionWrapper.eq("isDelete", 0);
        List<Question> newQuestions = questionService.list(questionWrapper);
        // 排除已用题目
        if (CollUtil.isNotEmpty(usedQuestionIds)) {
            newQuestions = newQuestions.stream()
                    .filter(q -> !usedQuestionIds.contains(q.getId()))
                    .collect(Collectors.toList());
        }
        ThrowUtils.throwIf(CollUtil.isEmpty(newQuestions), ErrorCode.OPERATION_ERROR, "未找到匹配薄弱标签的新题目");

        // 4. 获取相关帖子
        List<Post> posts = gatherPostsByTags(weakTags);

        // 5. 删除旧的待重新生成任务（硬删除）
        QueryWrapper<SprintPlanDailyTask> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("sprintPlanId", sprintPlanId);
        deleteWrapper.ge("dayNumber", fromDay);
        sprintPlanDailyTaskMapper.delete(deleteWrapper);

        // 6. 重新分配剩余天数
        int remainingDays = plan.getTotalDays() - fromDay + 1;
        SprintPlan tempPlan = new SprintPlan();
        tempPlan.setTotalDays(remainingDays);
        List<SprintPlanDailyTask> newTasks = distributeQuestions(tempPlan, newQuestions, posts, weakTags);

        // 7. 调整 dayNumber 偏移并保存
        for (SprintPlanDailyTask task : newTasks) {
            task.setDayNumber(task.getDayNumber() + fromDay - 1);
            task.setSprintPlanId(sprintPlanId);
            task.setUserId(loginUser.getId());
            sprintPlanDailyTaskMapper.insert(task);
        }

        return true;
    }

    /**
     * 获取计划进度
     */
    @Override
    public SprintPlanProgressVO getProgress(Long sprintPlanId, User loginUser) {
        SprintPlan plan = this.getById(sprintPlanId);
        ThrowUtils.throwIf(plan == null, ErrorCode.NOT_FOUND_ERROR, "计划不存在");
        ThrowUtils.throwIf(!plan.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);

        // 查询所有每日任务
        QueryWrapper<SprintPlanDailyTask> taskWrapper = new QueryWrapper<>();
        taskWrapper.eq("sprintPlanId", sprintPlanId);
        taskWrapper.orderByAsc("dayNumber");
        List<SprintPlanDailyTask> dailyTasks = sprintPlanDailyTaskMapper.selectList(taskWrapper);

        int completedDays = 0;
        List<SprintPlanProgressVO.DayStatus> dayStatusList = new ArrayList<>();
        for (SprintPlanDailyTask task : dailyTasks) {
            SprintPlanProgressVO.DayStatus dayStatus = new SprintPlanProgressVO.DayStatus();
            dayStatus.setDayNumber(task.getDayNumber());
            dayStatus.setIsCompleted(task.getIsCompleted());
            dayStatusList.add(dayStatus);
            if (task.getIsCompleted() == 1) {
                completedDays++;
            }
        }

        int totalDays = plan.getTotalDays();
        int progressPercent = (int) Math.round((double) completedDays / totalDays * 100);
        int currentDay = calcDayNumber(plan.getStartDate());
        currentDay = Math.max(1, Math.min(currentDay, totalDays));

        SprintPlanProgressVO progressVO = new SprintPlanProgressVO();
        progressVO.setSprintPlanId(sprintPlanId);
        progressVO.setTotalDays(totalDays);
        progressVO.setCompletedDays(completedDays);
        progressVO.setProgressPercent(progressPercent);
        progressVO.setCurrentDay(currentDay);
        progressVO.setStatus(plan.getStatus());
        progressVO.setDayStatusList(dayStatusList);

        return progressVO;
    }

    /**
     * 获取计划详情
     */
    @Override
    public SprintPlanVO getSprintPlanVO(Long sprintPlanId, User loginUser, HttpServletRequest request) {
        SprintPlan plan = this.getById(sprintPlanId);
        ThrowUtils.throwIf(plan == null, ErrorCode.NOT_FOUND_ERROR, "计划不存在");
        ThrowUtils.throwIf(!plan.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR);

        SprintPlanVO sprintPlanVO = SprintPlanVO.objToVo(plan);

        // 填充用户信息
        User user = userService.getById(plan.getUserId());
        sprintPlanVO.setUser(userService.getUserVO(user));

        // 填充每日任务
        QueryWrapper<SprintPlanDailyTask> taskWrapper = new QueryWrapper<>();
        taskWrapper.eq("sprintPlanId", sprintPlanId);
        taskWrapper.orderByAsc("dayNumber");
        List<SprintPlanDailyTask> dailyTasks = sprintPlanDailyTaskMapper.selectList(taskWrapper);
        List<SprintPlanDailyTaskVO> dailyTaskVOs = dailyTasks.stream()
                .map(this::enrichDailyTaskVO)
                .collect(Collectors.toList());
        sprintPlanVO.setDailyTasks(dailyTaskVOs);

        return sprintPlanVO;
    }

    // endregion

    // region 查询构建

    /**
     * 获取查询条件
     */
    @Override
    public QueryWrapper<SprintPlan> getQueryWrapper(SprintPlanQueryRequest queryRequest) {
        QueryWrapper<SprintPlan> queryWrapper = new QueryWrapper<>();
        if (queryRequest == null) {
            return queryWrapper;
        }
        Long id = queryRequest.getId();
        Integer status = queryRequest.getStatus();
        Long userId = queryRequest.getUserId();
        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();

        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(status), "status", status);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField),
                sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    /**
     * 分页获取封装类
     */
    @Override
    public Page<SprintPlanVO> getSprintPlanVOPage(Page<SprintPlan> sprintPlanPage, HttpServletRequest request) {
        List<SprintPlan> sprintPlanList = sprintPlanPage.getRecords();
        Page<SprintPlanVO> sprintPlanVOPage = new Page<>(sprintPlanPage.getCurrent(),
                sprintPlanPage.getSize(), sprintPlanPage.getTotal());
        if (CollUtil.isEmpty(sprintPlanList)) {
            return sprintPlanVOPage;
        }
        // 对象列表 => 封装对象列表
        List<SprintPlanVO> sprintPlanVOList = sprintPlanList.stream()
                .map(SprintPlanVO::objToVo)
                .collect(Collectors.toList());
        // 关联查询用户信息
        Set<Long> userIdSet = sprintPlanList.stream().map(SprintPlan::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        sprintPlanVOList.forEach(vo -> {
            Long userId = vo.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            vo.setUser(userService.getUserVO(user));
        });
        sprintPlanVOPage.setRecords(sprintPlanVOList);
        return sprintPlanVOPage;
    }

    // endregion

    // region 私有方法 - 题目和帖子收集

    /**
     * 从各来源收集题目
     */
    private List<Question> gatherQuestions(List<Long> questionBankIds, List<String> tags,
                                           Boolean includeFavouritePosts, Boolean includeMockInterviews,
                                           User loginUser) {
        Set<Long> questionIdSet = new HashSet<>();

        // 来源1：题库
        if (CollUtil.isNotEmpty(questionBankIds)) {
            for (Long bankId : questionBankIds) {
                LambdaQueryWrapper<QuestionBankQuestion> lambdaWrapper = Wrappers.lambdaQuery(QuestionBankQuestion.class)
                        .select(QuestionBankQuestion::getQuestionId)
                        .eq(QuestionBankQuestion::getQuestionBankId, bankId);
                List<QuestionBankQuestion> bankQuestions = questionBankQuestionService.list(lambdaWrapper);
                Set<Long> ids = bankQuestions.stream()
                        .map(QuestionBankQuestion::getQuestionId)
                        .collect(Collectors.toSet());
                questionIdSet.addAll(ids);
            }
        }

        // 来源2：标签
        if (CollUtil.isNotEmpty(tags)) {
            QueryWrapper<Question> tagWrapper = new QueryWrapper<>();
            for (String tag : tags) {
                tagWrapper.like("tags", "\"" + tag + "\"");
            }
            tagWrapper.select("id");
            List<Question> tagQuestions = questionService.list(tagWrapper);
            questionIdSet.addAll(tagQuestions.stream().map(Question::getId).collect(Collectors.toSet()));
        }

        // 来源3：收藏帖子关联的标签 -> 找相关题目
        if (Boolean.TRUE.equals(includeFavouritePosts)) {
            QueryWrapper<PostFavour> favourWrapper = new QueryWrapper<>();
            favourWrapper.eq("userId", loginUser.getId());
            favourWrapper.last("LIMIT 50");
            List<PostFavour> favours = postFavourService.list(favourWrapper);
            if (CollUtil.isNotEmpty(favours)) {
                Set<Long> postIds = favours.stream().map(PostFavour::getPostId).collect(Collectors.toSet());
                List<Post> favourPosts = postService.listByIds(postIds);
                // 从帖子标签中提取并查找相关题目
                Set<String> postTags = new HashSet<>();
                for (Post post : favourPosts) {
                    if (StrUtil.isNotBlank(post.getTags())) {
                        postTags.addAll(JSONUtil.toList(JSONUtil.parseArray(post.getTags()), String.class));
                    }
                }
                if (CollUtil.isNotEmpty(postTags)) {
                    QueryWrapper<Question> postTagWrapper = new QueryWrapper<>();
                    postTagWrapper.and(qw -> {
                        for (String tag : postTags) {
                            qw.or().like("tags", "\"" + tag + "\"");
                        }
                    });
                    postTagWrapper.select("id");
                    List<Question> relatedQuestions = questionService.list(postTagWrapper);
                    questionIdSet.addAll(relatedQuestions.stream().map(Question::getId).collect(Collectors.toSet()));
                }
            }
        }

        // 来源4：模拟面试历史 -> 提取岗位方向 -> 找相关题目
        if (Boolean.TRUE.equals(includeMockInterviews)) {
            QueryWrapper<MockInterview> mockWrapper = new QueryWrapper<>();
            mockWrapper.eq("userId", loginUser.getId());
            mockWrapper.eq("status", MockInterviewStatusEnum.ENDED.getValue());
            mockWrapper.eq("isDelete", 0);
            mockWrapper.orderByDesc("createTime");
            mockWrapper.last("LIMIT 10");
            List<MockInterview> interviews = mockInterviewService.list(mockWrapper);
            if (CollUtil.isNotEmpty(interviews)) {
                Set<String> jobTags = interviews.stream()
                        .map(MockInterview::getJobPosition)
                        .filter(StrUtil::isNotBlank)
                        .collect(Collectors.toSet());
                if (CollUtil.isNotEmpty(jobTags)) {
                    QueryWrapper<Question> jobWrapper = new QueryWrapper<>();
                    jobWrapper.and(qw -> {
                        for (String jobTag : jobTags) {
                            qw.or().like("tags", "\"" + jobTag + "\"");
                        }
                    });
                    jobWrapper.select("id");
                    List<Question> jobQuestions = questionService.list(jobWrapper);
                    questionIdSet.addAll(jobQuestions.stream().map(Question::getId).collect(Collectors.toSet()));
                }
            }
        }

        // 批量加载完整题目信息
        if (CollUtil.isEmpty(questionIdSet)) {
            return Collections.emptyList();
        }
        return questionService.listByIds(questionIdSet);
    }

    /**
     * 收集帖子
     */
    private List<Post> gatherPosts(Boolean includeFavouritePosts, List<String> tags, User loginUser) {
        Set<Long> postIdSet = new HashSet<>();

        // 收藏帖子
        if (Boolean.TRUE.equals(includeFavouritePosts)) {
            QueryWrapper<PostFavour> favourWrapper = new QueryWrapper<>();
            favourWrapper.eq("userId", loginUser.getId());
            favourWrapper.last("LIMIT 50");
            List<PostFavour> favours = postFavourService.list(favourWrapper);
            if (CollUtil.isNotEmpty(favours)) {
                postIdSet.addAll(favours.stream().map(PostFavour::getPostId).collect(Collectors.toSet()));
            }
        }

        // 标签匹配帖子
        if (CollUtil.isNotEmpty(tags)) {
            QueryWrapper<Post> postWrapper = new QueryWrapper<>();
            postWrapper.and(qw -> {
                for (String tag : tags) {
                    qw.or().like("tags", "\"" + tag + "\"");
                }
            });
            postWrapper.select("id");
            postWrapper.last("LIMIT 50");
            List<Post> tagPosts = postService.list(postWrapper);
            postIdSet.addAll(tagPosts.stream().map(Post::getId).collect(Collectors.toSet()));
        }

        if (CollUtil.isEmpty(postIdSet)) {
            return Collections.emptyList();
        }
        return postService.listByIds(postIdSet);
    }

    /**
     * 根据标签收集帖子
     */
    private List<Post> gatherPostsByTags(List<String> tags) {
        if (CollUtil.isEmpty(tags)) {
            return Collections.emptyList();
        }
        QueryWrapper<Post> postWrapper = new QueryWrapper<>();
        postWrapper.and(qw -> {
            for (String tag : tags) {
                qw.or().like("tags", "\"" + tag + "\"");
            }
        });
        postWrapper.last("LIMIT 50");
        return postService.list(postWrapper);
    }

    // endregion

    // region 私有方法 - 任务分配

    /**
     * 分配题目到每日任务（AI + 规则兜底）
     */
    private List<SprintPlanDailyTask> distributeQuestions(SprintPlan plan, List<Question> questions,
                                                          List<Post> posts, List<String> weakTags) {
        try {
            return distributeQuestionsWithAi(plan, questions, posts, weakTags);
        } catch (Exception e) {
            log.warn("AI 生成冲刺计划失败，使用规则兜底", e);
            return distributeQuestionsRuleBased(plan, questions, posts, weakTags);
        }
    }

    /**
     * AI 分配题目
     */
    private List<SprintPlanDailyTask> distributeQuestionsWithAi(SprintPlan plan, List<Question> questions,
                                                                 List<Post> posts, List<String> weakTags) {
        int totalDays = plan.getTotalDays();
        String userPrompt = buildAiUserPrompt(questions, posts, totalDays, weakTags);
        String aiResponse = aiManager.doChat(PLAN_GENERATION_SYSTEM_PROMPT, userPrompt);

        // 提取 JSON 内容（AI 可能包裹在 ```json ... ``` 中）
        String jsonStr = aiResponse.trim();
        if (jsonStr.contains("[")) {
            jsonStr = jsonStr.substring(jsonStr.indexOf("["), jsonStr.lastIndexOf("]") + 1);
        }

        // 构建有效 ID 集合用于校验
        Set<Long> validQuestionIds = questions.stream().map(Question::getId).collect(Collectors.toSet());
        Set<Long> validPostIds = posts.stream().map(Post::getId).collect(Collectors.toSet());

        JSONArray jsonArray = JSONUtil.parseArray(jsonStr);
        List<SprintPlanDailyTask> tasks = new ArrayList<>();
        for (int i = 0; i < jsonArray.size() && i < totalDays; i++) {
            JSONObject dayObj = jsonArray.getJSONObject(i);
            SprintPlanDailyTask task = new SprintPlanDailyTask();
            task.setDayNumber(dayObj.getInt("dayNumber", i + 1));

            // 校验题目 ID
            List<Long> qIds = dayObj.getJSONArray("questionIds").toList(Long.class);
            qIds = qIds.stream().filter(validQuestionIds::contains).collect(Collectors.toList());
            task.setQuestionIds(JSONUtil.toJsonStr(qIds));

            // 校验帖子 ID
            JSONArray postIdsArray = dayObj.getJSONArray("postIds");
            if (postIdsArray != null) {
                List<Long> pIds = postIdsArray.toList(Long.class);
                pIds = pIds.stream().filter(validPostIds::contains).collect(Collectors.toList());
                task.setPostIds(JSONUtil.toJsonStr(pIds));
            } else {
                task.setPostIds("[]");
            }

            task.setMockGoal(dayObj.getStr("mockGoal", "模拟面试练习"));
            task.setIsCompleted(0);
            tasks.add(task);
        }

        // 确保覆盖所有天数
        ThrowUtils.throwIf(tasks.size() < totalDays, ErrorCode.SYSTEM_ERROR, "AI 返回天数不足");
        return tasks;
    }

    /**
     * 规则分配题目（兜底方案）
     */
    private List<SprintPlanDailyTask> distributeQuestionsRuleBased(SprintPlan plan, List<Question> questions,
                                                                    List<Post> posts, List<String> weakTags) {
        int totalDays = plan.getTotalDays();

        // 1. 按首个标签分组
        LinkedHashMap<String, List<Question>> tagGroups = new LinkedHashMap<>();
        for (Question q : questions) {
            String primaryTag = "综合";
            if (StrUtil.isNotBlank(q.getTags())) {
                List<String> qTags = JSONUtil.toList(JSONUtil.parseArray(q.getTags()), String.class);
                if (CollUtil.isNotEmpty(qTags)) {
                    primaryTag = qTags.get(0);
                }
            }
            tagGroups.computeIfAbsent(primaryTag, k -> new ArrayList<>()).add(q);
        }

        // 2. 薄弱标签优先排序
        List<String> orderedTags = new ArrayList<>();
        if (CollUtil.isNotEmpty(weakTags)) {
            for (String wt : weakTags) {
                if (tagGroups.containsKey(wt)) {
                    orderedTags.add(wt);
                }
            }
        }
        for (String tag : tagGroups.keySet()) {
            if (!orderedTags.contains(tag)) {
                orderedTags.add(tag);
            }
        }

        // 3. 展开为有序列表
        List<Question> orderedQuestions = new ArrayList<>();
        for (String tag : orderedTags) {
            orderedQuestions.addAll(tagGroups.get(tag));
        }

        // 4. 均匀分配到每天
        int questionsPerDay = (int) Math.ceil((double) orderedQuestions.size() / totalDays);
        // 构建帖子标签索引
        Map<String, List<Post>> postsByTag = new LinkedHashMap<>();
        for (Post p : posts) {
            if (StrUtil.isNotBlank(p.getTags())) {
                List<String> pTags = JSONUtil.toList(JSONUtil.parseArray(p.getTags()), String.class);
                for (String pt : pTags) {
                    postsByTag.computeIfAbsent(pt, k -> new ArrayList<>()).add(p);
                }
            }
        }
        Set<Long> assignedPostIds = new HashSet<>();

        List<SprintPlanDailyTask> tasks = new ArrayList<>();
        for (int day = 1; day <= totalDays; day++) {
            int fromIndex = (day - 1) * questionsPerDay;
            int toIndex = Math.min(fromIndex + questionsPerDay, orderedQuestions.size());

            SprintPlanDailyTask task = new SprintPlanDailyTask();
            task.setDayNumber(day);
            task.setIsCompleted(0);

            // 分配题目
            List<Long> dayQuestionIds = new ArrayList<>();
            Set<String> dayTags = new HashSet<>();
            if (fromIndex < orderedQuestions.size()) {
                List<Question> dayQuestions = orderedQuestions.subList(fromIndex, toIndex);
                for (Question q : dayQuestions) {
                    dayQuestionIds.add(q.getId());
                    if (StrUtil.isNotBlank(q.getTags())) {
                        dayTags.addAll(JSONUtil.toList(JSONUtil.parseArray(q.getTags()), String.class));
                    }
                }
            }
            task.setQuestionIds(JSONUtil.toJsonStr(dayQuestionIds));

            // 分配帖子（按标签匹配，每天最多3个）
            List<Long> dayPostIds = new ArrayList<>();
            for (String dt : dayTags) {
                if (dayPostIds.size() >= 3) break;
                List<Post> matchedPosts = postsByTag.getOrDefault(dt, Collections.emptyList());
                for (Post mp : matchedPosts) {
                    if (dayPostIds.size() >= 3) break;
                    if (!assignedPostIds.contains(mp.getId())) {
                        dayPostIds.add(mp.getId());
                        assignedPostIds.add(mp.getId());
                    }
                }
            }
            task.setPostIds(JSONUtil.toJsonStr(dayPostIds));

            // 生成模拟面试目标
            if (CollUtil.isNotEmpty(dayTags)) {
                List<String> tagList = new ArrayList<>(dayTags);
                String mockGoal = "模拟面试重点: " + String.join(", ",
                        tagList.subList(0, Math.min(tagList.size(), 3)));
                task.setMockGoal(mockGoal);
            } else {
                task.setMockGoal("综合模拟面试练习");
            }

            tasks.add(task);
        }

        return tasks;
    }

    /**
     * 构建 AI 用户提示词
     */
    private String buildAiUserPrompt(List<Question> questions, List<Post> posts,
                                      int totalDays, List<String> weakTags) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("总天数：%d\n\n", totalDays));
        if (CollUtil.isNotEmpty(weakTags)) {
            sb.append("薄弱标签（需重点安排）：").append(String.join(", ", weakTags)).append("\n\n");
        }
        sb.append("题目列表：\n");
        for (Question q : questions) {
            sb.append(String.format("- ID:%d, 标题:%s, 标签:%s\n", q.getId(), q.getTitle(), q.getTags()));
        }
        if (CollUtil.isNotEmpty(posts)) {
            sb.append("\n帖子列表：\n");
            for (Post p : posts) {
                sb.append(String.format("- ID:%d, 标题:%s, 标签:%s\n", p.getId(), p.getTitle(), p.getTags()));
            }
        }
        return sb.toString();
    }

    // endregion

    // region 私有方法 - 工具方法

    /**
     * 计算当前是计划的第几天
     */
    private int calcDayNumber(Date startDate) {
        long diffDays = DateUtil.betweenDay(startDate, new Date(), true);
        return (int) diffDays + 1;
    }

    /**
     * 填充每日任务详情
     */
    private SprintPlanDailyTaskVO enrichDailyTaskVO(SprintPlanDailyTask dailyTask) {
        SprintPlanDailyTaskVO vo = SprintPlanDailyTaskVO.objToVo(dailyTask);

        // 填充题目详情
        List<Long> questionIdList = vo.getQuestionIdList();
        if (CollUtil.isNotEmpty(questionIdList)) {
            List<Question> questions = questionService.listByIds(questionIdList);
            List<QuestionVO> questionVOs = questions.stream()
                    .map(QuestionVO::objToVo)
                    .collect(Collectors.toList());
            vo.setQuestions(questionVOs);
        }

        // 填充帖子详情
        List<Long> postIdList = vo.getPostIdList();
        if (CollUtil.isNotEmpty(postIdList)) {
            List<Post> posts = postService.listByIds(postIdList);
            List<PostVO> postVOs = posts.stream()
                    .map(PostVO::objToVo)
                    .collect(Collectors.toList());
            vo.setPosts(postVOs);
        }

        return vo;
    }

    // endregion
}
