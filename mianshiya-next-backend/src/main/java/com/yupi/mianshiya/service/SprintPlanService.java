package com.yupi.mianshiya.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.mianshiya.model.dto.sprintplan.SprintPlanAddRequest;
import com.yupi.mianshiya.model.dto.sprintplan.SprintPlanCompleteRequest;
import com.yupi.mianshiya.model.dto.sprintplan.SprintPlanQueryRequest;
import com.yupi.mianshiya.model.dto.sprintplan.SprintPlanRegenerateRequest;
import com.yupi.mianshiya.model.entity.SprintPlan;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.model.vo.SprintPlanDailyTaskVO;
import com.yupi.mianshiya.model.vo.SprintPlanProgressVO;
import com.yupi.mianshiya.model.vo.SprintPlanVO;

import javax.servlet.http.HttpServletRequest;

/**
 * 面试冲刺计划服务
 */
public interface SprintPlanService extends IService<SprintPlan> {

    /**
     * 创建冲刺计划
     */
    Long createSprintPlan(SprintPlanAddRequest addRequest, User loginUser);

    /**
     * 获取今日任务
     */
    SprintPlanDailyTaskVO getTodayTask(Long sprintPlanId, User loginUser, HttpServletRequest request);

    /**
     * 完成任务
     */
    Boolean completeTask(SprintPlanCompleteRequest completeRequest, User loginUser);

    /**
     * 重新生成薄弱标签任务
     */
    Boolean regenerateWeakTagTasks(SprintPlanRegenerateRequest regenerateRequest, User loginUser);

    /**
     * 获取计划进度
     */
    SprintPlanProgressVO getProgress(Long sprintPlanId, User loginUser);

    /**
     * 获取计划详情（封装类）
     */
    SprintPlanVO getSprintPlanVO(Long sprintPlanId, User loginUser, HttpServletRequest request);

    /**
     * 获取查询条件
     */
    QueryWrapper<SprintPlan> getQueryWrapper(SprintPlanQueryRequest queryRequest);

    /**
     * 分页获取封装类
     */
    Page<SprintPlanVO> getSprintPlanVOPage(Page<SprintPlan> sprintPlanPage, HttpServletRequest request);
}
