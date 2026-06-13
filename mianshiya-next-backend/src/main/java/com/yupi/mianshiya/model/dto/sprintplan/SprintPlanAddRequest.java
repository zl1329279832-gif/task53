package com.yupi.mianshiya.model.dto.sprintplan;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 创建冲刺计划请求
 */
@Data
public class SprintPlanAddRequest implements Serializable {

    /**
     * 计划标题
     */
    private String title;

    /**
     * 计划总天数（7或14）
     */
    private Integer totalDays;

    /**
     * 题库 id 列表
     */
    private List<Long> questionBankIds;

    /**
     * 目标标签列表
     */
    private List<String> tags;

    /**
     * 是否包含收藏帖子
     */
    private Boolean includeFavouritePosts;

    /**
     * 是否包含模拟面试记录
     */
    private Boolean includeMockInterviews;

    private static final long serialVersionUID = 1L;
}
