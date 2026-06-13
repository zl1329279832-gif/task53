package com.yupi.mianshiya.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.mianshiya.common.BaseResponse;
import com.yupi.mianshiya.common.ErrorCode;
import com.yupi.mianshiya.common.ResultUtils;
import com.yupi.mianshiya.exception.ThrowUtils;
import com.yupi.mianshiya.model.dto.sprintplan.SprintPlanAddRequest;
import com.yupi.mianshiya.model.dto.sprintplan.SprintPlanCompleteRequest;
import com.yupi.mianshiya.model.dto.sprintplan.SprintPlanQueryRequest;
import com.yupi.mianshiya.model.dto.sprintplan.SprintPlanRegenerateRequest;
import com.yupi.mianshiya.model.entity.SprintPlan;
import com.yupi.mianshiya.model.entity.User;
import com.yupi.mianshiya.model.vo.SprintPlanDailyTaskVO;
import com.yupi.mianshiya.model.vo.SprintPlanProgressVO;
import com.yupi.mianshiya.model.vo.SprintPlanVO;
import com.yupi.mianshiya.service.SprintPlanService;
import com.yupi.mianshiya.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 面试冲刺计划接口
 */
@RestController
@RequestMapping("/sprintPlan")
@Slf4j
public class SprintPlanController {

    @Resource
    private SprintPlanService sprintPlanService;

    @Resource
    private UserService userService;

    /**
     * 创建冲刺计划
     */
    @PostMapping("/create")
    public BaseResponse<Long> createSprintPlan(@RequestBody SprintPlanAddRequest sprintPlanAddRequest,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(sprintPlanAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long sprintPlanId = sprintPlanService.createSprintPlan(sprintPlanAddRequest, loginUser);
        return ResultUtils.success(sprintPlanId);
    }

    /**
     * 获取今日任务
     */
    @GetMapping("/today")
    public BaseResponse<SprintPlanDailyTaskVO> getTodayTask(long sprintPlanId, HttpServletRequest request) {
        ThrowUtils.throwIf(sprintPlanId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        SprintPlanDailyTaskVO todayTask = sprintPlanService.getTodayTask(sprintPlanId, loginUser, request);
        return ResultUtils.success(todayTask);
    }

    /**
     * 完成任务
     */
    @PostMapping("/complete")
    public BaseResponse<Boolean> completeTask(@RequestBody SprintPlanCompleteRequest completeRequest,
                                              HttpServletRequest request) {
        ThrowUtils.throwIf(completeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Boolean result = sprintPlanService.completeTask(completeRequest, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 重新生成薄弱标签任务
     */
    @PostMapping("/regenerate")
    public BaseResponse<Boolean> regenerateWeakTagTasks(@RequestBody SprintPlanRegenerateRequest regenerateRequest,
                                                        HttpServletRequest request) {
        ThrowUtils.throwIf(regenerateRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Boolean result = sprintPlanService.regenerateWeakTagTasks(regenerateRequest, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 获取计划进度
     */
    @GetMapping("/progress")
    public BaseResponse<SprintPlanProgressVO> getProgress(long sprintPlanId, HttpServletRequest request) {
        ThrowUtils.throwIf(sprintPlanId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        SprintPlanProgressVO progress = sprintPlanService.getProgress(sprintPlanId, loginUser);
        return ResultUtils.success(progress);
    }

    /**
     * 根据 id 获取冲刺计划（封装类）
     */
    @GetMapping("/get/vo")
    public BaseResponse<SprintPlanVO> getSprintPlanVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        SprintPlanVO sprintPlanVO = sprintPlanService.getSprintPlanVO(id, loginUser, request);
        return ResultUtils.success(sprintPlanVO);
    }

    /**
     * 分页获取当前登录用户创建的冲刺计划列表
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<SprintPlanVO>> listMySprintPlanVOByPage(@RequestBody SprintPlanQueryRequest sprintPlanQueryRequest,
                                                                     HttpServletRequest request) {
        ThrowUtils.throwIf(sprintPlanQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long size = sprintPlanQueryRequest.getPageSize();
        long current = sprintPlanQueryRequest.getCurrent();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 限制只能获取本人的
        User loginUser = userService.getLoginUser(request);
        sprintPlanQueryRequest.setUserId(loginUser.getId());
        // 查询数据库
        Page<SprintPlan> sprintPlanPage = sprintPlanService.page(
                new Page<>(current, size),
                sprintPlanService.getQueryWrapper(sprintPlanQueryRequest)
        );
        // 获取封装类
        return ResultUtils.success(sprintPlanService.getSprintPlanVOPage(sprintPlanPage, request));
    }
}
