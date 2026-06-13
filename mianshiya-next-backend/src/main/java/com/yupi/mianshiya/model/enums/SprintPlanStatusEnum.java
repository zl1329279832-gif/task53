package com.yupi.mianshiya.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * 冲刺计划状态枚举
 */
@Getter
public enum SprintPlanStatusEnum {

    IN_PROGRESS("进行中", 0),
    COMPLETED("已完成", 1),
    ABANDONED("已废弃", 2);

    private final String text;
    private final int value;

    SprintPlanStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 获取值列表
     */
    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(SprintPlanStatusEnum::getValue).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     */
    public static SprintPlanStatusEnum getEnumByValue(int value) {
        for (SprintPlanStatusEnum e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
