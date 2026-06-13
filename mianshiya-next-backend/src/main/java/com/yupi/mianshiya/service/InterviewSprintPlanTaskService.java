package com.yupi.mianshiya.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.mianshiya.model.entity.InterviewSprintPlanTask;
import com.yupi.mianshiya.model.vo.sprintplan.InterviewSprintPlanTaskVO;

import java.util.List;

/**
 * 面试冲刺计划每日任务服务
 */
public interface InterviewSprintPlanTaskService extends IService<InterviewSprintPlanTask> {

    /**
     * 批量创建每日任务
     *
     * @param tasks 任务列表
     */
    void batchCreateTasks(List<InterviewSprintPlanTask> tasks);

    /**
     * 获取计划下所有任务（展开为 VO）
     *
     * @param planId 计划 id
     * @param userId 用户 id
     * @return 任务 VO 列表
     */
    List<InterviewSprintPlanTaskVO> listTasksByPlanId(Long planId, Long userId);

    /**
     * 展开 questionIds / postIds 为完整 VO 对象
     *
     * @param task 任务实体
     * @return 任务 VO
     */
    InterviewSprintPlanTaskVO enrichTaskVO(InterviewSprintPlanTask task);
}
