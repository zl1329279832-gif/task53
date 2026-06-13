package com.yupi.mianshiya.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;

/**
 * 面试冲刺计划状态枚举
 */
public enum SprintPlanStatusEnum {

    IN_PROGRESS("进行中", 0),
    COMPLETED("已完成", 1),
    ABANDONED("已放弃", 2);

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
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     */
    public static SprintPlanStatusEnum getEnumByValue(Integer value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (SprintPlanStatusEnum anEnum : SprintPlanStatusEnum.values()) {
            if (anEnum.value == value) {
                return anEnum;
            }
        }
        return null;
    }

    public int getValue() {
        return value;
    }

    public String getText() {
        return text;
    }
}
