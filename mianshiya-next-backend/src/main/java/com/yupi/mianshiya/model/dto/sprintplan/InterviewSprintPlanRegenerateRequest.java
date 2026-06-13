package com.yupi.mianshiya.model.dto.sprintplan;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 重新生成弱项标签任务请求
 */
@Data
public class InterviewSprintPlanRegenerateRequest implements Serializable {

    /**
     * 计划 id
     */
    private Long planId;

    /**
     * 需要重新生成的天数编号列表（可选，为空则自动识别弱项天）
     */
    private List<Integer> dayNumbers;

    private static final long serialVersionUID = 1L;
}
