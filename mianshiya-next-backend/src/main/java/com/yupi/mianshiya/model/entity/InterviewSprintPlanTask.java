package com.yupi.mianshiya.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 面试冲刺计划每日任务
 *
 * @TableName interview_sprint_plan_task
 */
@TableName(value = "interview_sprint_plan_task")
@Data
public class InterviewSprintPlanTask implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属计划 id
     */
    private Long planId;

    /**
     * 用户 id（冗余，便于权限校验）
     */
    private Long userId;

    /**
     * 第几天，从 1 开始
     */
    private Integer dayNumber;

    /**
     * 任务日期
     */
    private Date taskDate;

    /**
     * 题目 id 列表，JSON 数组
     */
    private String questionIds;

    /**
     * 推荐帖子 id 列表，JSON 数组
     */
    private String postIds;

    /**
     * 模拟面试目标描述
     */
    private String mockInterviewGoal;

    /**
     * 学习要点
     */
    private String studyNotes;

    /**
     * 完成状态：0-未完成, 1-已完成
     */
    private Integer status;

    /**
     * 完成时间
     */
    private Date completedTime;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
