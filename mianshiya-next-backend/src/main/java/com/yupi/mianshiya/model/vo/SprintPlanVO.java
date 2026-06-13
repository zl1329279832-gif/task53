package com.yupi.mianshiya.model.vo;

import cn.hutool.json.JSONUtil;
import com.yupi.mianshiya.model.entity.SprintPlan;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import lombok.Data;
import org.springframework.beans.BeanUtils;

/**
 * 冲刺计划视图
 */
@Data
public class SprintPlanVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 计划标题
     */
    private String title;

    /**
     * 计划总天数
     */
    private Integer totalDays;

    /**
     * 题库 id 列表
     */
    private List<Long> questionBankIdList;

    /**
     * 标签列表
     */
    private List<String> tagList;

    /**
     * 状态
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
     * 每日任务列表
     */
    private List<SprintPlanDailyTaskVO> dailyTasks;

    /**
     * 创建用户信息
     */
    private UserVO user;

    /**
     * 对象转封装类
     */
    public static SprintPlanVO objToVo(SprintPlan sprintPlan) {
        if (sprintPlan == null) {
            return null;
        }
        SprintPlanVO sprintPlanVO = new SprintPlanVO();
        BeanUtils.copyProperties(sprintPlan, sprintPlanVO);
        String tags = sprintPlan.getTags();
        if (tags != null) {
            sprintPlanVO.setTagList(JSONUtil.toList(JSONUtil.parseArray(tags), String.class));
        }
        String questionBankIds = sprintPlan.getQuestionBankIds();
        if (questionBankIds != null) {
            sprintPlanVO.setQuestionBankIdList(JSONUtil.toList(JSONUtil.parseArray(questionBankIds), Long.class));
        }
        return sprintPlanVO;
    }

    /**
     * 封装类转对象
     */
    public static SprintPlan voToObj(SprintPlanVO sprintPlanVO) {
        if (sprintPlanVO == null) {
            return null;
        }
        SprintPlan sprintPlan = new SprintPlan();
        BeanUtils.copyProperties(sprintPlanVO, sprintPlan);
        List<String> tagList = sprintPlanVO.getTagList();
        if (tagList != null) {
            sprintPlan.setTags(JSONUtil.toJsonStr(tagList));
        }
        List<Long> questionBankIdList = sprintPlanVO.getQuestionBankIdList();
        if (questionBankIdList != null) {
            sprintPlan.setQuestionBankIds(JSONUtil.toJsonStr(questionBankIdList));
        }
        return sprintPlan;
    }

    private static final long serialVersionUID = 1L;
}
