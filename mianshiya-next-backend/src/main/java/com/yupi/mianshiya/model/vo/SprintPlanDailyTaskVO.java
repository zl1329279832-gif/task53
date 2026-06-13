package com.yupi.mianshiya.model.vo;

import cn.hutool.json.JSONUtil;
import com.yupi.mianshiya.model.entity.SprintPlanDailyTask;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import lombok.Data;
import org.springframework.beans.BeanUtils;

/**
 * 冲刺计划每日任务视图
 */
@Data
public class SprintPlanDailyTaskVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 冲刺计划 id
     */
    private Long sprintPlanId;

    /**
     * 第几天
     */
    private Integer dayNumber;

    /**
     * 题目 id 列表
     */
    private List<Long> questionIdList;

    /**
     * 帖子 id 列表
     */
    private List<Long> postIdList;

    /**
     * 模拟面试目标
     */
    private String mockGoal;

    /**
     * 是否完成
     */
    private Integer isCompleted;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 题目详情列表
     */
    private List<QuestionVO> questions;

    /**
     * 帖子详情列表
     */
    private List<PostVO> posts;

    /**
     * 对象转封装类
     */
    public static SprintPlanDailyTaskVO objToVo(SprintPlanDailyTask dailyTask) {
        if (dailyTask == null) {
            return null;
        }
        SprintPlanDailyTaskVO vo = new SprintPlanDailyTaskVO();
        BeanUtils.copyProperties(dailyTask, vo);
        String questionIds = dailyTask.getQuestionIds();
        if (questionIds != null) {
            vo.setQuestionIdList(JSONUtil.toList(JSONUtil.parseArray(questionIds), Long.class));
        }
        String postIds = dailyTask.getPostIds();
        if (postIds != null) {
            vo.setPostIdList(JSONUtil.toList(JSONUtil.parseArray(postIds), Long.class));
        }
        return vo;
    }

    /**
     * 封装类转对象
     */
    public static SprintPlanDailyTask voToObj(SprintPlanDailyTaskVO vo) {
        if (vo == null) {
            return null;
        }
        SprintPlanDailyTask dailyTask = new SprintPlanDailyTask();
        BeanUtils.copyProperties(vo, dailyTask);
        List<Long> questionIdList = vo.getQuestionIdList();
        if (questionIdList != null) {
            dailyTask.setQuestionIds(JSONUtil.toJsonStr(questionIdList));
        }
        List<Long> postIdList = vo.getPostIdList();
        if (postIdList != null) {
            dailyTask.setPostIds(JSONUtil.toJsonStr(postIdList));
        }
        return dailyTask;
    }

    private static final long serialVersionUID = 1L;
}
