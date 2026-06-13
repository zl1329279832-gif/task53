package com.yupi.mianshiya.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.mapper.SprintPlanDailyTaskMapper;
import com.yupi.mianshiya.model.dto.sprintplan.SprintPlanAddRequest;
import com.yupi.mianshiya.model.dto.sprintplan.SprintPlanCompleteRequest;
import com.yupi.mianshiya.model.dto.sprintplan.SprintPlanRegenerateRequest;
import com.yupi.mianshiya.model.entity.SprintPlan;
import com.yupi.mianshiya.model.entity.SprintPlanDailyTask;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.model.enums.SprintPlanStatusEnum;
import com.yupi.mianshiya.model.vo.SprintPlanProgressVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 面试冲刺计划服务测试
 */
@SpringBootTest
class SprintPlanServiceTest {

    @Resource
    private SprintPlanService sprintPlanService;

    @Resource
    private SprintPlanDailyTaskMapper sprintPlanDailyTaskMapper;

    private static final User loginUser = new User();
    private static final User otherUser = new User();

    @BeforeAll
    static void setUp() {
        loginUser.setId(1L);
        otherUser.setId(2L);
    }

    @Test
    void testCreateSprintPlan7Day() {
        // 先清理可能存在的进行中计划
        cleanActivePlans(loginUser.getId());

        SprintPlanAddRequest addRequest = new SprintPlanAddRequest();
        addRequest.setTitle("7天Java冲刺");
        addRequest.setTotalDays(7);
        addRequest.setTags(Arrays.asList("Java"));
        addRequest.setIncludeFavouritePosts(false);
        addRequest.setIncludeMockInterviews(false);

        Long planId = sprintPlanService.createSprintPlan(addRequest, loginUser);
        Assertions.assertNotNull(planId);
        Assertions.assertTrue(planId > 0);

        // 验证计划
        SprintPlan plan = sprintPlanService.getById(planId);
        Assertions.assertNotNull(plan);
        Assertions.assertEquals(7, plan.getTotalDays());
        Assertions.assertEquals(SprintPlanStatusEnum.IN_PROGRESS.getValue(), plan.getStatus());

        // 验证每日任务数量
        QueryWrapper<SprintPlanDailyTask> wrapper = new QueryWrapper<>();
        wrapper.eq("sprintPlanId", planId);
        Long taskCount = sprintPlanDailyTaskMapper.selectCount(wrapper);
        Assertions.assertEquals(7, taskCount);

        // 清理
        cleanPlan(planId);
    }

    @Test
    void testCreateSprintPlan14Day() {
        cleanActivePlans(loginUser.getId());

        SprintPlanAddRequest addRequest = new SprintPlanAddRequest();
        addRequest.setTitle("14天全面冲刺");
        addRequest.setTotalDays(14);
        addRequest.setTags(Arrays.asList("Java", "Spring"));
        addRequest.setIncludeFavouritePosts(true);
        addRequest.setIncludeMockInterviews(true);

        Long planId = sprintPlanService.createSprintPlan(addRequest, loginUser);
        Assertions.assertNotNull(planId);

        SprintPlan plan = sprintPlanService.getById(planId);
        Assertions.assertEquals(14, plan.getTotalDays());

        QueryWrapper<SprintPlanDailyTask> wrapper = new QueryWrapper<>();
        wrapper.eq("sprintPlanId", planId);
        Long taskCount = sprintPlanDailyTaskMapper.selectCount(wrapper);
        Assertions.assertEquals(14, taskCount);

        cleanPlan(planId);
    }

    @Test
    void testCreateSprintPlanInvalidDays() {
        cleanActivePlans(loginUser.getId());

        SprintPlanAddRequest addRequest = new SprintPlanAddRequest();
        addRequest.setTitle("错误天数");
        addRequest.setTotalDays(10);
        addRequest.setTags(Arrays.asList("Java"));

        Assertions.assertThrows(BusinessException.class, () -> {
            sprintPlanService.createSprintPlan(addRequest, loginUser);
        });
    }

    @Test
    void testDuplicatePlanPrevention() {
        cleanActivePlans(loginUser.getId());

        // 创建第一个计划
        SprintPlanAddRequest addRequest = new SprintPlanAddRequest();
        addRequest.setTitle("第一个计划");
        addRequest.setTotalDays(7);
        addRequest.setTags(Arrays.asList("Java"));

        Long planId = sprintPlanService.createSprintPlan(addRequest, loginUser);
        Assertions.assertNotNull(planId);

        // 尝试创建第二个计划，应该失败
        SprintPlanAddRequest secondRequest = new SprintPlanAddRequest();
        secondRequest.setTitle("第二个计划");
        secondRequest.setTotalDays(7);
        secondRequest.setTags(Arrays.asList("Spring"));

        Assertions.assertThrows(BusinessException.class, () -> {
            sprintPlanService.createSprintPlan(secondRequest, loginUser);
        });

        cleanPlan(planId);
    }

    @Test
    void testPermissionIsolation() {
        cleanActivePlans(loginUser.getId());

        SprintPlanAddRequest addRequest = new SprintPlanAddRequest();
        addRequest.setTitle("权限测试计划");
        addRequest.setTotalDays(7);
        addRequest.setTags(Arrays.asList("Java"));

        Long planId = sprintPlanService.createSprintPlan(addRequest, loginUser);

        // 其他用户访问应报错
        Assertions.assertThrows(BusinessException.class, () -> {
            sprintPlanService.getProgress(planId, otherUser);
        });

        cleanPlan(planId);
    }

    @Test
    void testPermissionIsolationComplete() {
        cleanActivePlans(loginUser.getId());

        SprintPlanAddRequest addRequest = new SprintPlanAddRequest();
        addRequest.setTitle("权限完成测试");
        addRequest.setTotalDays(7);
        addRequest.setTags(Arrays.asList("Java"));

        Long planId = sprintPlanService.createSprintPlan(addRequest, loginUser);

        // 其他用户完成任务应报错
        SprintPlanCompleteRequest completeRequest = new SprintPlanCompleteRequest();
        completeRequest.setSprintPlanId(planId);
        completeRequest.setDayNumber(1);

        Assertions.assertThrows(BusinessException.class, () -> {
            sprintPlanService.completeTask(completeRequest, otherUser);
        });

        cleanPlan(planId);
    }

    @Test
    void testProgressCalculation() {
        cleanActivePlans(loginUser.getId());

        SprintPlanAddRequest addRequest = new SprintPlanAddRequest();
        addRequest.setTitle("进度测试");
        addRequest.setTotalDays(7);
        addRequest.setTags(Arrays.asList("Java"));

        Long planId = sprintPlanService.createSprintPlan(addRequest, loginUser);

        // 完成第1天
        SprintPlanCompleteRequest completeRequest = new SprintPlanCompleteRequest();
        completeRequest.setSprintPlanId(planId);
        completeRequest.setDayNumber(1);
        sprintPlanService.completeTask(completeRequest, loginUser);

        // 检查进度
        SprintPlanProgressVO progress = sprintPlanService.getProgress(planId, loginUser);
        Assertions.assertEquals(7, progress.getTotalDays());
        Assertions.assertEquals(1, progress.getCompletedDays());
        Assertions.assertEquals(14, progress.getProgressPercent());

        cleanPlan(planId);
    }

    @Test
    void testCompletePlanAutoStatus() {
        cleanActivePlans(loginUser.getId());

        SprintPlanAddRequest addRequest = new SprintPlanAddRequest();
        addRequest.setTitle("自动完成测试");
        addRequest.setTotalDays(7);
        addRequest.setTags(Arrays.asList("Java"));

        Long planId = sprintPlanService.createSprintPlan(addRequest, loginUser);

        // 完成所有7天
        for (int day = 1; day <= 7; day++) {
            SprintPlanCompleteRequest completeRequest = new SprintPlanCompleteRequest();
            completeRequest.setSprintPlanId(planId);
            completeRequest.setDayNumber(day);
            sprintPlanService.completeTask(completeRequest, loginUser);
        }

        // 检查计划状态自动变为已完成
        SprintPlan plan = sprintPlanService.getById(planId);
        Assertions.assertEquals(SprintPlanStatusEnum.COMPLETED.getValue(), plan.getStatus());

        cleanPlan(planId);
    }

    // region 清理方法

    private void cleanActivePlans(Long userId) {
        QueryWrapper<SprintPlan> wrapper = new QueryWrapper<>();
        wrapper.eq("userId", userId);
        wrapper.eq("status", SprintPlanStatusEnum.IN_PROGRESS.getValue());
        List<SprintPlan> plans = sprintPlanService.list(wrapper);
        for (SprintPlan plan : plans) {
            cleanPlan(plan.getId());
        }
    }

    private void cleanPlan(Long planId) {
        // 删除每日任务
        QueryWrapper<SprintPlanDailyTask> taskWrapper = new QueryWrapper<>();
        taskWrapper.eq("sprintPlanId", planId);
        sprintPlanDailyTaskMapper.delete(taskWrapper);
        // 删除计划
        sprintPlanService.removeById(planId);
    }

    // endregion
}
