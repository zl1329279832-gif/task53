package com.yupi.mianshiya.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.model.dto.sprintplan.*;
import com.yupi.mianshiya.model.entity.InterviewSprintPlan;
import com.yupi.mianshiya.model.entity.InterviewSprintPlanTask;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.model.enums.SprintPlanSourceTypeEnum;
import com.yupi.mianshiya.model.enums.SprintPlanStatusEnum;
import com.yupi.mianshiya.model.enums.SprintPlanTaskStatusEnum;
import com.yupi.mianshiya.model.vo.sprintplan.InterviewSprintPlanTaskVO;
import com.yupi.mianshiya.model.vo.sprintplan.InterviewSprintPlanVO;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * 面试冲刺计划服务测试
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InterviewSprintPlanServiceTest {

    @Resource
    private InterviewSprintPlanService sprintPlanService;

    @Resource
    private InterviewSprintPlanTaskService planTaskService;

    @Resource
    private UserService userService;

    private static MockHttpServletRequest mockRequest;

    /**
     * 测试用的用户 id（需要在数据库中存在）
     * 使用 init_data.sql 中的第一个用户 id=1
     */
    private static final Long TEST_USER_ID = 1L;

    /**
     * 第二个用户 id（权限隔离测试用）
     */
    private static final Long OTHER_USER_ID = 2L;

    @BeforeAll
    static void setUp() {
        mockRequest = new MockHttpServletRequest();
    }

    @BeforeEach
    void loginBeforeEach() {
        // 使用 Sa-Token 登录测试用户
        StpUtil.login(TEST_USER_ID);
    }

    // ======================== 参数校验 ========================

    @Test
    @Order(1)
    void testValidCreateRequest_invalidDuration_throwsError() {
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(5); // 非法
        request.setSourceType(0);
        request.setSourceId(1L);
        Assertions.assertThrows(BusinessException.class,
                () -> sprintPlanService.validCreateRequest(request));
    }

    @Test
    @Order(2)
    void testValidCreateRequest_nullSourceType_throwsError() {
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(null);
        Assertions.assertThrows(BusinessException.class,
                () -> sprintPlanService.validCreateRequest(request));
    }

    @Test
    @Order(3)
    void testValidCreateRequest_questionBankWithoutId_throwsError() {
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(0);
        request.setSourceId(null);
        Assertions.assertThrows(BusinessException.class,
                () -> sprintPlanService.validCreateRequest(request));
    }

    @Test
    @Order(4)
    void testValidCreateRequest_tagWithoutTags_throwsError() {
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(1);
        request.setTags(null);
        Assertions.assertThrows(BusinessException.class,
                () -> sprintPlanService.validCreateRequest(request));
    }

    @Test
    @Order(5)
    void testValidCreateRequest_validParams_passes() {
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(0);
        request.setSourceId(1L);
        // 不应抛异常
        Assertions.assertDoesNotThrow(() -> sprintPlanService.validCreateRequest(request));
    }

    // ======================== 计划生成（标签来源）========================

    @Test
    @Order(10)
    void testCreateSprintPlan_fromTags_success() {
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(SprintPlanSourceTypeEnum.TAG.getValue());
        request.setTags(Arrays.asList("Java", "Spring"));
        request.setStartDate(LocalDate.now().toString());

        Long planId = sprintPlanService.createSprintPlan(request, mockRequest);
        Assertions.assertNotNull(planId);

        // 验证计划创建正确
        InterviewSprintPlan plan = sprintPlanService.getById(planId);
        Assertions.assertNotNull(plan);
        Assertions.assertEquals(7, plan.getDuration());
        Assertions.assertEquals(0, plan.getCompletedCount());
        Assertions.assertEquals(SprintPlanStatusEnum.IN_PROGRESS.getValue(), plan.getStatus());
        Assertions.assertEquals(TEST_USER_ID, plan.getUserId());

        // 验证每日任务创建
        List<InterviewSprintPlanTask> tasks = planTaskService.list(
                new LambdaQueryWrapper<InterviewSprintPlanTask>()
                        .eq(InterviewSprintPlanTask::getPlanId, planId)
                        .orderByAsc(InterviewSprintPlanTask::getDayNumber));
        Assertions.assertEquals(7, tasks.size());

        // 验证 dayNumber 从 1 到 7
        for (int i = 0; i < tasks.size(); i++) {
            Assertions.assertEquals(i + 1, tasks.get(i).getDayNumber());
            Assertions.assertEquals(SprintPlanTaskStatusEnum.PENDING.getValue(), tasks.get(i).getStatus());
        }

        // 清理：废弃计划以便后续测试不受影响
        plan.setStatus(SprintPlanStatusEnum.ABANDONED.getValue());
        sprintPlanService.updateById(plan);
    }

    @Test
    @Order(11)
    void testCreateSprintPlan_14days_success() {
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(14);
        request.setSourceType(SprintPlanSourceTypeEnum.TAG.getValue());
        request.setTags(Arrays.asList("算法"));
        request.setStartDate(LocalDate.now().toString());

        Long planId = sprintPlanService.createSprintPlan(request, mockRequest);
        Assertions.assertNotNull(planId);

        InterviewSprintPlan plan = sprintPlanService.getById(planId);
        Assertions.assertEquals(14, plan.getDuration());

        List<InterviewSprintPlanTask> tasks = planTaskService.list(
                new LambdaQueryWrapper<InterviewSprintPlanTask>()
                        .eq(InterviewSprintPlanTask::getPlanId, planId));
        Assertions.assertEquals(14, tasks.size());

        // 清理
        plan.setStatus(SprintPlanStatusEnum.ABANDONED.getValue());
        sprintPlanService.updateById(plan);
    }

    // ======================== 重复保护 ========================

    @Test
    @Order(20)
    void testCreateSprintPlan_duplicatePrevented() {
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(SprintPlanSourceTypeEnum.TAG.getValue());
        request.setTags(Arrays.asList("重复测试"));
        request.setStartDate(LocalDate.now().toString());

        // 第一次创建应成功
        Long planId = sprintPlanService.createSprintPlan(request, mockRequest);
        Assertions.assertNotNull(planId);

        // 第二次创建同类型计划应失败（重复保护）
        Assertions.assertThrows(BusinessException.class,
                () -> sprintPlanService.createSprintPlan(request, mockRequest));

        // 清理
        InterviewSprintPlan plan = sprintPlanService.getById(planId);
        plan.setStatus(SprintPlanStatusEnum.ABANDONED.getValue());
        sprintPlanService.updateById(plan);
    }

    // ======================== 进度统计 ========================

    @Test
    @Order(30)
    void testProgressStats_completePartial() {
        // 创建 7 天计划
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(SprintPlanSourceTypeEnum.TAG.getValue());
        request.setTags(Arrays.asList("进度测试"));
        request.setStartDate(LocalDate.now().toString());

        Long planId = sprintPlanService.createSprintPlan(request, mockRequest);

        // 获取任务列表
        List<InterviewSprintPlanTask> tasks = planTaskService.list(
                new LambdaQueryWrapper<InterviewSprintPlanTask>()
                        .eq(InterviewSprintPlanTask::getPlanId, planId)
                        .orderByAsc(InterviewSprintPlanTask::getDayNumber));
        Assertions.assertTrue(tasks.size() >= 3);

        // 标记前 3 天完成
        for (int i = 0; i < 3; i++) {
            InterviewSprintPlanTaskCompleteRequest completeReq = new InterviewSprintPlanTaskCompleteRequest();
            completeReq.setTaskId(tasks.get(i).getId());
            sprintPlanService.completeTask(completeReq, mockRequest);
        }

        // 验证进度
        InterviewSprintPlanVO detail = sprintPlanService.getSprintPlanDetail(planId, mockRequest);
        Assertions.assertEquals(3, detail.getCompletedCount());
        Assertions.assertEquals(42, detail.getProgressPercent()); // 3/7 ≈ 42%

        // 清理
        InterviewSprintPlan plan = sprintPlanService.getById(planId);
        plan.setStatus(SprintPlanStatusEnum.ABANDONED.getValue());
        sprintPlanService.updateById(plan);
    }

    @Test
    @Order(31)
    void testProgressStats_allCompleted() {
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(SprintPlanSourceTypeEnum.TAG.getValue());
        request.setTags(Arrays.asList("全完成测试"));
        request.setStartDate(LocalDate.now().toString());

        Long planId = sprintPlanService.createSprintPlan(request, mockRequest);

        // 完成所有 7 天
        List<InterviewSprintPlanTask> tasks = planTaskService.list(
                new LambdaQueryWrapper<InterviewSprintPlanTask>()
                        .eq(InterviewSprintPlanTask::getPlanId, planId)
                        .orderByAsc(InterviewSprintPlanTask::getDayNumber));
        for (InterviewSprintPlanTask task : tasks) {
            InterviewSprintPlanTaskCompleteRequest completeReq = new InterviewSprintPlanTaskCompleteRequest();
            completeReq.setTaskId(task.getId());
            sprintPlanService.completeTask(completeReq, mockRequest);
        }

        // 验证计划状态变为已完成
        InterviewSprintPlan plan = sprintPlanService.getById(planId);
        Assertions.assertEquals(SprintPlanStatusEnum.COMPLETED.getValue(), plan.getStatus());
        Assertions.assertEquals(7, plan.getCompletedCount());
    }

    // ======================== 标记完成（幂等）========================

    @Test
    @Order(40)
    void testCompleteTask_idempotent() {
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(SprintPlanSourceTypeEnum.TAG.getValue());
        request.setTags(Arrays.asList("幂等测试"));
        request.setStartDate(LocalDate.now().toString());

        Long planId = sprintPlanService.createSprintPlan(request, mockRequest);

        InterviewSprintPlanTask task = planTaskService.getOne(
                new LambdaQueryWrapper<InterviewSprintPlanTask>()
                        .eq(InterviewSprintPlanTask::getPlanId, planId)
                        .eq(InterviewSprintPlanTask::getDayNumber, 1));

        InterviewSprintPlanTaskCompleteRequest completeReq = new InterviewSprintPlanTaskCompleteRequest();
        completeReq.setTaskId(task.getId());

        // 第一次完成
        sprintPlanService.completeTask(completeReq, mockRequest);
        // 第二次完成（幂等）
        sprintPlanService.completeTask(completeReq, mockRequest);

        // completedCount 应该仍为 1
        InterviewSprintPlan plan = sprintPlanService.getById(planId);
        Assertions.assertEquals(1, plan.getCompletedCount());

        // 验证任务状态
        InterviewSprintPlanTask updatedTask = planTaskService.getById(task.getId());
        Assertions.assertEquals(SprintPlanTaskStatusEnum.COMPLETED.getValue(), updatedTask.getStatus());
        Assertions.assertNotNull(updatedTask.getCompletedTime());
    }

    // ======================== 权限隔离 ========================

    @Test
    @Order(50)
    void testPermissionIsolation_otherUserCannotAccess() {
        // 以当前用户创建计划
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(SprintPlanSourceTypeEnum.TAG.getValue());
        request.setTags(Arrays.asList("权限测试"));
        request.setStartDate(LocalDate.now().toString());

        Long planId = sprintPlanService.createSprintPlan(request, mockRequest);

        // 切换到另一个用户
        StpUtil.logout();
        StpUtil.login(OTHER_USER_ID);

        // 另一个用户不应能查看详情
        Assertions.assertThrows(BusinessException.class,
                () -> sprintPlanService.getSprintPlanDetail(planId, mockRequest));

        // 另一个用户不应能查看今日任务
        Assertions.assertThrows(BusinessException.class,
                () -> sprintPlanService.getTodayTask(planId, mockRequest));

        // 另一个用户不应能标记完成
        InterviewSprintPlanTask task = planTaskService.getOne(
                new LambdaQueryWrapper<InterviewSprintPlanTask>()
                        .eq(InterviewSprintPlanTask::getPlanId, planId)
                        .eq(InterviewSprintPlanTask::getDayNumber, 1));
        InterviewSprintPlanTaskCompleteRequest completeReq = new InterviewSprintPlanTaskCompleteRequest();
        completeReq.setTaskId(task.getId());
        Assertions.assertThrows(BusinessException.class,
                () -> sprintPlanService.completeTask(completeReq, mockRequest));

        // 恢复登录
        StpUtil.logout();
        StpUtil.login(TEST_USER_ID);

        // 清理
        InterviewSprintPlan plan = sprintPlanService.getById(planId);
        plan.setStatus(SprintPlanStatusEnum.ABANDONED.getValue());
        sprintPlanService.updateById(plan);
    }

    @Test
    @Order(51)
    void testPermissionIsolation_otherUserCannotRegenerate() {
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(SprintPlanSourceTypeEnum.TAG.getValue());
        request.setTags(Arrays.asList("权限重生成测试"));
        request.setStartDate(LocalDate.now().toString());

        Long planId = sprintPlanService.createSprintPlan(request, mockRequest);

        // 切换到另一个用户
        StpUtil.logout();
        StpUtil.login(OTHER_USER_ID);

        InterviewSprintPlanRegenerateRequest regenReq = new InterviewSprintPlanRegenerateRequest();
        regenReq.setPlanId(planId);
        regenReq.setDayNumbers(Arrays.asList(1, 2));
        Assertions.assertThrows(BusinessException.class,
                () -> sprintPlanService.regenerateWeakTagTasks(regenReq, mockRequest));

        // 恢复登录
        StpUtil.logout();
        StpUtil.login(TEST_USER_ID);

        // 清理
        InterviewSprintPlan plan = sprintPlanService.getById(planId);
        plan.setStatus(SprintPlanStatusEnum.ABANDONED.getValue());
        sprintPlanService.updateById(plan);
    }

    // ======================== 今日任务 ========================

    @Test
    @Order(60)
    void testGetTodayTask_success() {
        // 创建从今天开始的计划
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(SprintPlanSourceTypeEnum.TAG.getValue());
        request.setTags(Arrays.asList("今日测试"));
        request.setStartDate(LocalDate.now().toString());

        Long planId = sprintPlanService.createSprintPlan(request, mockRequest);

        // 获取今日任务
        InterviewSprintPlanTaskVO todayTask = sprintPlanService.getTodayTask(planId, mockRequest);
        Assertions.assertNotNull(todayTask);
        Assertions.assertEquals(1, todayTask.getDayNumber());
        Assertions.assertNotNull(todayTask.getMockInterviewGoal());

        // 清理
        InterviewSprintPlan plan = sprintPlanService.getById(planId);
        plan.setStatus(SprintPlanStatusEnum.ABANDONED.getValue());
        sprintPlanService.updateById(plan);
    }

    @Test
    @Order(61)
    void testGetTodayTask_outOfRange_throwsError() {
        // 创建 30 天前开始的计划
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(SprintPlanSourceTypeEnum.TAG.getValue());
        request.setTags(Arrays.asList("过期测试"));
        request.setStartDate(LocalDate.now().minusDays(30).toString());

        Long planId = sprintPlanService.createSprintPlan(request, mockRequest);

        // 今天不在计划范围内
        Assertions.assertThrows(BusinessException.class,
                () -> sprintPlanService.getTodayTask(planId, mockRequest));

        // 清理
        InterviewSprintPlan plan = sprintPlanService.getById(planId);
        plan.setStatus(SprintPlanStatusEnum.ABANDONED.getValue());
        sprintPlanService.updateById(plan);
    }

    // ======================== 重新生成弱项任务 ========================

    @Test
    @Order(70)
    void testRegenerateWeakTagTasks_success() {
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(SprintPlanSourceTypeEnum.TAG.getValue());
        request.setTags(Arrays.asList("重生成测试"));
        request.setStartDate(LocalDate.now().toString());

        Long planId = sprintPlanService.createSprintPlan(request, mockRequest);

        // 获取第 2 天和第 4 天的原始任务
        InterviewSprintPlanTask task2Before = planTaskService.getOne(
                new LambdaQueryWrapper<InterviewSprintPlanTask>()
                        .eq(InterviewSprintPlanTask::getPlanId, planId)
                        .eq(InterviewSprintPlanTask::getDayNumber, 2));
        InterviewSprintPlanTask task4Before = planTaskService.getOne(
                new LambdaQueryWrapper<InterviewSprintPlanTask>()
                        .eq(InterviewSprintPlanTask::getPlanId, planId)
                        .eq(InterviewSprintPlanTask::getDayNumber, 4));

        // 重新生成第 2、4 天
        InterviewSprintPlanRegenerateRequest regenReq = new InterviewSprintPlanRegenerateRequest();
        regenReq.setPlanId(planId);
        regenReq.setDayNumbers(Arrays.asList(2, 4));
        sprintPlanService.regenerateWeakTagTasks(regenReq, mockRequest);

        // 验证任务状态被重置为 PENDING
        InterviewSprintPlanTask task2After = planTaskService.getById(task2Before.getId());
        InterviewSprintPlanTask task4After = planTaskService.getById(task4Before.getId());
        Assertions.assertEquals(SprintPlanTaskStatusEnum.PENDING.getValue(), task2After.getStatus());
        Assertions.assertEquals(SprintPlanTaskStatusEnum.PENDING.getValue(), task4After.getStatus());
        Assertions.assertNull(task2After.getCompletedTime());
        Assertions.assertNull(task4After.getCompletedTime());

        // 清理
        InterviewSprintPlan plan = sprintPlanService.getById(planId);
        plan.setStatus(SprintPlanStatusEnum.ABANDONED.getValue());
        sprintPlanService.updateById(plan);
    }

    @Test
    @Order(71)
    void testRegenerateWeakTagTasks_autoIdentifyWeakDays() {
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(SprintPlanSourceTypeEnum.TAG.getValue());
        request.setTags(Arrays.asList("自动识别测试"));
        request.setStartDate(LocalDate.now().toString());

        Long planId = sprintPlanService.createSprintPlan(request, mockRequest);

        // 不指定 dayNumbers，自动识别未完成天
        InterviewSprintPlanRegenerateRequest regenReq = new InterviewSprintPlanRegenerateRequest();
        regenReq.setPlanId(planId);
        // dayNumbers 为空，应自动识别
        Assertions.assertDoesNotThrow(
                () -> sprintPlanService.regenerateWeakTagTasks(regenReq, mockRequest));

        // 清理
        InterviewSprintPlan plan = sprintPlanService.getById(planId);
        plan.setStatus(SprintPlanStatusEnum.ABANDONED.getValue());
        sprintPlanService.updateById(plan);
    }

    // ======================== 分页查询 ========================

    @Test
    @Order(80)
    void testListSprintPlanByPage() {
        // 先创建一个计划
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(SprintPlanSourceTypeEnum.TAG.getValue());
        request.setTags(Arrays.asList("分页测试"));
        request.setStartDate(LocalDate.now().toString());

        Long planId = sprintPlanService.createSprintPlan(request, mockRequest);

        // 分页查询
        InterviewSprintPlanQueryRequest queryRequest = new InterviewSprintPlanQueryRequest();
        queryRequest.setCurrent(1);
        queryRequest.setPageSize(10);
        Page<InterviewSprintPlanVO> page = sprintPlanService.listSprintPlanByPage(queryRequest, mockRequest);

        Assertions.assertNotNull(page);
        Assertions.assertTrue(page.getTotal() >= 1);
        Assertions.assertFalse(page.getRecords().isEmpty());

        // 验证返回的 VO 有 progressPercent
        InterviewSprintPlanVO firstVO = page.getRecords().get(0);
        Assertions.assertNotNull(firstVO.getProgressPercent());

        // 清理
        InterviewSprintPlan plan = sprintPlanService.getById(planId);
        plan.setStatus(SprintPlanStatusEnum.ABANDONED.getValue());
        sprintPlanService.updateById(plan);
    }

    // ======================== 查询计划详情 ========================

    @Test
    @Order(90)
    void testGetSprintPlanDetail_success() {
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(SprintPlanSourceTypeEnum.TAG.getValue());
        request.setTags(Arrays.asList("详情测试"));
        request.setStartDate(LocalDate.now().toString());

        Long planId = sprintPlanService.createSprintPlan(request, mockRequest);

        InterviewSprintPlanVO detail = sprintPlanService.getSprintPlanDetail(planId, mockRequest);
        Assertions.assertNotNull(detail);
        Assertions.assertNotNull(detail.getTasks());
        Assertions.assertEquals(7, detail.getTasks().size());
        Assertions.assertEquals(0, detail.getCompletedCount());
        Assertions.assertEquals(0, detail.getProgressPercent());

        // 验证每个 task VO 都包含 questions 和 posts 列表（可能为空）
        for (InterviewSprintPlanTaskVO taskVO : detail.getTasks()) {
            Assertions.assertNotNull(taskVO.getQuestions());
            Assertions.assertNotNull(taskVO.getPosts());
            Assertions.assertNotNull(taskVO.getMockInterviewGoal());
        }

        // 清理
        InterviewSprintPlan plan = sprintPlanService.getById(planId);
        plan.setStatus(SprintPlanStatusEnum.ABANDONED.getValue());
        sprintPlanService.updateById(plan);
    }

    // ======================== 空来源测试 ========================

    @Test
    @Order(100)
    void testCreateSprintPlan_emptySource_throwsError() {
        // 使用不存在的题库 id
        InterviewSprintPlanCreateRequest request = new InterviewSprintPlanCreateRequest();
        request.setDuration(7);
        request.setSourceType(SprintPlanSourceTypeEnum.QUESTION_BANK.getValue());
        request.setSourceId(999999L);
        request.setStartDate(LocalDate.now().toString());

        Assertions.assertThrows(BusinessException.class,
                () -> sprintPlanService.createSprintPlan(request, mockRequest));
    }
}
