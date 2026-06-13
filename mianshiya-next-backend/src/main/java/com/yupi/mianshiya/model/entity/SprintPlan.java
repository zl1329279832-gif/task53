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
 * @TableName sprint_plan
 */
@TableName(value = "sprint_plan")
@Data
public class SprintPlan implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 计划标题
     */
    private String title;

    /**
     * 计划总天数（7或14）
     */
    private Integer totalDays;

    /**
     * 题库 id 列表（JSON 数组）
     */
    private String questionBankIds;

    /**
     * 目标标签列表（JSON 数组）
     */
    private String tags;

    /**
     * 计划配置信息（JSON）
     */
    private String config;

    /**
     * 状态（0-进行中、1-已完成、2-已放弃）
     */
    private Integer status;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 计划开始日期
     */
    private Date startDate;

    /**
     * 计划结束日期
     */
    private Date endDate;

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
