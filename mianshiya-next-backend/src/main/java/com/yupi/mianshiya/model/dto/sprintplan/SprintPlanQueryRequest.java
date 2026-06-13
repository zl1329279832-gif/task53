package com.yupi.mianshiya.model.dto.sprintplan;

import com.yupi.mianshiya.common.PageRequest;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 查询冲刺计划请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SprintPlanQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 用户 id
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}
