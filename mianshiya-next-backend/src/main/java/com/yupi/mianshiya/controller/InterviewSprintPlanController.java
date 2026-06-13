package com.yupi.mianshiya.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.mianshiya.common.BaseResponse;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.common.ResultUtils;
import com.yupi.mianshiya.exception.BusinessException;
import com.yupi.mianshiya.exception.ThrowUtils;
import com.yupi.mianshiya.model.dto.sprintplan.InterviewSprintPlanCreateRequest;
import com.yupi.mianshiya.model.dto.sprintplan.InterviewSprintPlanQueryRequest;
import com.yupi.mianshiya.model.dto.sprintplan.InterviewSprintPlanRegenerateRequest;
import com.yupi.mianshiya.model.dto.sprintplan.InterviewSprintPlanTaskCompleteRequest;
import com.yupi.mianshiya.model.vo.sprintplan.InterviewSprintPlanTaskVO;
import com.yupi.mianshiya.model.vo.sprintplan.InterviewSprintPlanVO;
import com.yupi.mianshiya.service.InterviewSprintPlanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 面试冲刺计划接口
 */
@RestController
@RequestMapping("/sprint-plan")
@Slf4j
public class InterviewSprintPlanController {

    @Resource
    private InterviewSprintPlanService interviewSprintPlanService;

    // region 增删改查

    /**
     * 创建冲刺计划
     *
     * @param request     创建请求
     * @param httpRequest HTTP 请求
     * @return 计划 id
     */
    @PostMapping("/create")
    public BaseResponse<Long> createSprintPlan(@RequestBody InterviewSprintPlanCreateRequest request,
                                               HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        Long planId = interviewSprintPlanService.createSprintPlan(request, httpRequest);
        return ResultUtils.success(planId);
    }

    /**
     * 获取今日任务
     *
     * @param planId      计划 id
     * @param httpRequest HTTP 请求
     * @return 今日任务 VO
     */
    @GetMapping("/today")
    public BaseResponse<InterviewSprintPlanTaskVO> getTodayTask(@RequestParam Long planId,
                                                                HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(planId == null || planId <= 0, ErrorCode.PARAMS_ERROR);
        InterviewSprintPlanTaskVO taskVO = interviewSprintPlanService.getTodayTask(planId, httpRequest);
        return ResultUtils.success(taskVO);
    }

    /**
     * 标记任务完成
     *
     * @param request     完成请求
     * @param httpRequest HTTP 请求
     * @return 是否成功
     */
    @PostMapping("/complete")
    public BaseResponse<Boolean> completeTask(@RequestBody InterviewSprintPlanTaskCompleteRequest request,
                                              HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null || request.getTaskId() == null, ErrorCode.PARAMS_ERROR);
        interviewSprintPlanService.completeTask(request, httpRequest);
        return ResultUtils.success(true);
    }

    /**
     * 重新生成弱项标签任务
     *
     * @param request     重新生成请求
     * @param httpRequest HTTP 请求
     * @return 是否成功
     */
    @PostMapping("/regenerate")
    public BaseResponse<Boolean> regenerateWeakTagTasks(@RequestBody InterviewSprintPlanRegenerateRequest request,
                                                        HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null || request.getPlanId() == null, ErrorCode.PARAMS_ERROR);
        interviewSprintPlanService.regenerateWeakTagTasks(request, httpRequest);
        return ResultUtils.success(true);
    }

    /**
     * 查询计划详情（含所有每日任务和进度）
     *
     * @param planId      计划 id
     * @param httpRequest HTTP 请求
     * @return 计划详情 VO
     */
    @GetMapping("/detail")
    public BaseResponse<InterviewSprintPlanVO> getSprintPlanDetail(@RequestParam Long planId,
                                                                   HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(planId == null || planId <= 0, ErrorCode.PARAMS_ERROR);
        InterviewSprintPlanVO planVO = interviewSprintPlanService.getSprintPlanDetail(planId, httpRequest);
        return ResultUtils.success(planVO);
    }

    /**
     * 分页查询计划列表
     *
     * @param request     查询请求
     * @param httpRequest HTTP 请求
     * @return 分页结果
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<InterviewSprintPlanVO>> listSprintPlanByPage(
            @RequestBody InterviewSprintPlanQueryRequest request,
            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        long size = request.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<InterviewSprintPlanVO> result = interviewSprintPlanService.listSprintPlanByPage(request, httpRequest);
        return ResultUtils.success(result);
    }

    // endregion
}
