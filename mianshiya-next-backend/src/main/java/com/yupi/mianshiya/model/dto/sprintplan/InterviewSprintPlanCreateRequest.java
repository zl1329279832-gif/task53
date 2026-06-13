package com.yupi.mianshiya.model.dto.sprintplan;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建面试冲刺计划请求
 */
@Data
public class InterviewSprintPlanCreateRequest implements Serializable {

    /**
     * 计划天数：7 或 14
     */
    private Integer duration;

    /**
     * 来源类型：0-题库, 1-标签, 2-收藏帖子, 3-模拟面试记录
     */
    private Integer sourceType;

    /**
     * 来源 id（题库 id / 模拟面试 id，标签和收藏为 null）
     */
    private Long sourceId;

    /**
     * 标签列表（sourceType=1 时必填）
     */
    private List<String> tags;

    /**
     * 计划开始日期，格式 yyyy-MM-dd，默认今天
     */
    private String startDate;

    private static final long serialVersionUID = 1L;
}
