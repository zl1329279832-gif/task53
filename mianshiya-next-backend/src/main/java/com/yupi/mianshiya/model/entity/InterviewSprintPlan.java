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
 * 面试冲刺计划
 *
 * @TableName interview_sprint_plan
 */
@TableName(value = "interview_sprint_plan")
@Data
public class InterviewSprintPlan implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 计划名称
     */
    private String planName;

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
     * 计划开始日期
     */
    private Date startDate;

    /**
     * 计划结束日期
     */
    private Date endDate;

    /**
     * 计划总题目数
     */
    private Integer totalQuestions;

    /**
     * 已完成天数
     */
    private Integer completedCount;

    /**
     * 状态：0-进行中, 1-已完成, 2-已废弃
     */
    private Integer status;

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
