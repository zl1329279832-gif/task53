package com.yupi.mianshiya.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 面试冲刺计划每日任务
 *
 * @TableName sprint_plan_daily_task
 */
@TableName(value = "sprint_plan_daily_task")
@Data
public class SprintPlanDailyTask implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 冲刺计划 id
     */
    private Long sprintPlanId;

    /**
     * 第几天（从1开始）
     */
    private Integer dayNumber;

    /**
     * 题目 id 列表（JSON 数组）
     */
    private String questionIds;

    /**
     * 推荐帖子 id 列表（JSON 数组）
     */
    private String postIds;

    /**
     * 模拟面试目标描述
     */
    private String mockGoal;

    /**
     * 是否完成（0-未完成、1-已完成）
     */
    private Integer isCompleted;

    /**
     * 创建用户 id
     */
    private Long userId;

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
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
