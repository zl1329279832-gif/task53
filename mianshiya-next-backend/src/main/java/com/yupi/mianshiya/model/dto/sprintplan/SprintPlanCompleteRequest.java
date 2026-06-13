package com.yupi.mianshiya.model.dto.sprintplan;

import java.io.Serializable;
import lombok.Data;

/**
 * 完成冲刺计划任务请求
 */
@Data
public class SprintPlanCompleteRequest implements Serializable {

    /**
     * 冲刺计划 id
     */
    private Long sprintPlanId;

    /**
     * 第几天
     */
    private Integer dayNumber;

    private static final long serialVersionUID = 1L;
}
