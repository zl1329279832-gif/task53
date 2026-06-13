package com.yupi.mianshiya.model.dto.sprintplan;

import lombok.Data;

import java.io.Serializable;

/**
 * 标记冲刺计划任务完成请求
 */
@Data
public class InterviewSprintPlanTaskCompleteRequest implements Serializable {

    /**
     * 任务 id
     */
    private Long taskId;

    private static final long serialVersionUID = 1L;
}
