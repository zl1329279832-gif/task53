package com.yupi.mianshiya.model.dto.sprintplan;

import java.io.Serializable;
import java.util.List;
import lombok.Data;

/**
 * 重新生成薄弱标签任务请求
 */
@Data
public class SprintPlanRegenerateRequest implements Serializable {

    /**
     * 冲刺计划 id
     */
    private Long sprintPlanId;

    /**
     * 薄弱标签列表
     */
    private List<String> weakTags;

    /**
     * 从第几天开始重新生成（包含）
     */
    private Integer fromDay;

    private static final long serialVersionUID = 1L;
}
