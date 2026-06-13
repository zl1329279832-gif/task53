package com.yupi.mianshiya.model.vo.sprintplan;

import com.yupi.mianshiya.model.vo.PostVO;
import com.yupi.mianshiya.model.vo.QuestionVO;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 面试冲刺计划每日任务视图
 */
@Data
public class InterviewSprintPlanTaskVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 所属计划 id
     */
    private Long planId;

    /**
     * 第几天
     */
    private Integer dayNumber;

    /**
     * 任务日期
     */
    private Date taskDate;

    /**
     * 完成状态：0-未完成, 1-已完成
     */
    private Integer status;

    /**
     * 完成时间
     */
    private Date completedTime;

    /**
     * 展开的题目列表
     */
    private List<QuestionVO> questions;

    /**
     * 展开的帖子列表
     */
    private List<PostVO> posts;

    /**
     * 模拟面试目标
     */
    private String mockInterviewGoal;

    /**
     * 学习要点
     */
    private String studyNotes;

    private static final long serialVersionUID = 1L;
}
