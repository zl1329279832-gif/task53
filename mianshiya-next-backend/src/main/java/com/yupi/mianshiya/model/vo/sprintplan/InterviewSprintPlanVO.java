package com.yupi.mianshiya.model.vo.sprintplan;

import com.yupi.mianshiya.model.entity.InterviewSprintPlan;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 面试冲刺计划视图
 */
@Data
public class InterviewSprintPlanVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 计划名称
     */
    private String planName;

    /**
     * 计划天数
     */
    private Integer duration;

    /**
     * 来源类型
     */
    private Integer sourceType;

    /**
     * 来源 id
     */
    private Long sourceId;

    /**
     * 开始日期
     */
    private Date startDate;

    /**
     * 结束日期
     */
    private Date endDate;

    /**
     * 总题目数
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
     * 每日任务列表（查询详情时填充）
     */
    private List<InterviewSprintPlanTaskVO> tasks;

    /**
     * 进度百分比 (0-100)
     */
    private Integer progressPercent;

    /**
     * 对象转封装类
     */
    public static InterviewSprintPlanVO objToVo(InterviewSprintPlan plan) {
        if (plan == null) {
            return null;
        }
        InterviewSprintPlanVO vo = new InterviewSprintPlanVO();
        BeanUtils.copyProperties(plan, vo);
        // 计算进度百分比
        if (plan.getDuration() != null && plan.getDuration() > 0) {
            vo.setProgressPercent(plan.getCompletedCount() * 100 / plan.getDuration());
        } else {
            vo.setProgressPercent(0);
        }
        return vo;
    }

    private static final long serialVersionUID = 1L;
}
