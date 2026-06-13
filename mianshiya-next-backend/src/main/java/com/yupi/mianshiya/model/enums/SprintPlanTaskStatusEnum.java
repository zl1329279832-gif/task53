package com.yupi.mianshiya.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * 冲刺计划任务完成状态枚举
 */
@Getter
public enum SprintPlanTaskStatusEnum {

    PENDING("未完成", 0),
    COMPLETED("已完成", 1);

    private final String text;
    private final int value;

    SprintPlanTaskStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 获取值列表
     */
    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(SprintPlanTaskStatusEnum::getValue).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     */
    public static SprintPlanTaskStatusEnum getEnumByValue(int value) {
        for (SprintPlanTaskStatusEnum e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
