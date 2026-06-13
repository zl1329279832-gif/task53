package com.yupi.mianshiya.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.mianshiya.model.dto.sprintplan.InterviewSprintPlanCreateRequest;
import com.yupi.mianshiya.model.dto.sprintplan.InterviewSprintPlanQueryRequest;
import com.yupi.mianshiya.model.dto.sprintplan.InterviewSprintPlanRegenerateRequest;
import com.yupi.mianshiya.model.dto.sprintplan.InterviewSprintPlanTaskCompleteRequest;
import com.yupi.mianshiya.model.entity.InterviewSprintPlan;
import com.yupi.mianshiya.model.vo.sprintplan.InterviewSprintPlanTaskVO;
import com.yupi.mianshiya.model.vo.sprintplan.InterviewSprintPlanVO;

import javax.servlet.http.HttpServletRequest;

/**
 * 面试冲刺计划服务
 */
public interface InterviewSprintPlanService extends IService<InterviewSprintPlan> {

    /**
     * 创建冲刺计划（含 AI 生成每日任务）
     *
     * @param request     创建请求
     * @param httpRequest HTTP 请求
     * @return 计划 id
     */
    Long createSprintPlan(InterviewSprintPlanCreateRequest request, HttpServletRequest httpRequest);

    /**
     * 获取今日任务
     *
     * @param planId      计划 id
     * @param httpRequest HTTP 请求
     * @return 今日任务 VO
     */
    InterviewSprintPlanTaskVO getTodayTask(Long planId, HttpServletRequest httpRequest);

    /**
     * 标记某日任务完成
     *
     * @param request     完成请求
     * @param httpRequest HTTP 请求
     */
    void completeTask(InterviewSprintPlanTaskCompleteRequest request, HttpServletRequest httpRequest);

    /**
     * 根据弱项标签重新生成某些天的任务
     *
     * @param request     重新生成请求
     * @param httpRequest HTTP 请求
     */
    void regenerateWeakTagTasks(InterviewSprintPlanRegenerateRequest request, HttpServletRequest httpRequest);

    /**
     * 查询计划详情（含所有每日任务）
     *
     * @param planId      计划 id
     * @param httpRequest HTTP 请求
     * @return 计划详情 VO
     */
    InterviewSprintPlanVO getSprintPlanDetail(Long planId, HttpServletRequest httpRequest);

    /**
     * 分页查询用户的计划列表
     *
     * @param request     查询请求
     * @param httpRequest HTTP 请求
     * @return 分页结果
     */
    Page<InterviewSprintPlanVO> listSprintPlanByPage(InterviewSprintPlanQueryRequest request,
                                                     HttpServletRequest httpRequest);

    /**
     * 校验计划是否存在且属于当前用户
     *
     * @param planId 计划 id
     * @param userId 用户 id
     * @return 计划实体
     */
    InterviewSprintPlan getValidPlan(Long planId, Long userId);

    /**
     * 校验创建请求参数
     *
     * @param request 创建请求
     */
    void validCreateRequest(InterviewSprintPlanCreateRequest request);
}
