package com.yupi.mianshiya.model.dto.sprintplan;

import com.yupi.mianshiya.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询面试冲刺计划请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class InterviewSprintPlanQueryRequest extends PageRequest implements Serializable {

    /**
     * 计划 id
     */
    private Long id;

    /**
     * 状态：0-进行中, 1-已完成, 2-已废弃
     */
    private Integer status;

    /**
     * 来源类型：0-题库, 1-标签, 2-收藏帖子, 3-模拟面试记录
     */
    private Integer sourceType;

    private static final long serialVersionUID = 1L;
}
