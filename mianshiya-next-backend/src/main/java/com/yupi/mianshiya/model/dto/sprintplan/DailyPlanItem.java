package com.yupi.mianshiya.model.dto.sprintplan;

import lombok.Data;

import java.util.List;

/**
 * 每日计划项（内部 DTO，AI/规则生成结果）
 */
@Data
public class DailyPlanItem {

    /**
     * 题目 id 列表
     */
    private List<Long> questionIds;

    /**
     * 帖子 id 列表
     */
    private List<Long> postIds;

    /**
     * 模拟面试目标
     */
    private String mockInterviewGoal;

    /**
     * 学习要点
     */
    private String studyNotes;
}
