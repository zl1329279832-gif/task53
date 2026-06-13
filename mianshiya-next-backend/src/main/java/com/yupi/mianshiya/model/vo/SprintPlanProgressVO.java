package com.yupi.mianshiya.model.vo;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 冲刺计划进度视图
 */
@Data
public class SprintPlanProgressVO implements Serializable {

    /**
     * 冲刺计划 id
     */
    private Long sprintPlanId;

    /**
     * 计划总天数
     */
    private Integer totalDays;

    /**
     * 已完成天数
     */
    private Integer completedDays;

    /**
     * 进度百分比（0-100）
     */
    private Integer progressPercent;

    /**
     * 当前是第几天（基于开始日期计算）
     */
    private Integer currentDay;

    /**
     * 计划状态
     */
    private Integer status;

    /**
     * 每日完成状态列表
     */
    private List<DayStatus> dayStatusList;

    /**
     * 每日状态
     */
    @Data
    public static class DayStatus implements Serializable {

        /**
         * 第几天
         */
        private Integer dayNumber;

        /**
         * 是否完成
         */
        private Integer isCompleted;

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
