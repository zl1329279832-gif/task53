package com.yupi.mianshiya.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * 冲刺计划来源类型枚举
 */
@Getter
public enum SprintPlanSourceTypeEnum {

    QUESTION_BANK("题库", 0),
    TAG("标签", 1),
    FAVOURITED_POST("收藏帖子", 2),
    MOCK_INTERVIEW("模拟面试记录", 3);

    private final String text;
    private final int value;

    SprintPlanSourceTypeEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 获取值列表
     */
    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(SprintPlanSourceTypeEnum::getValue).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     */
    public static SprintPlanSourceTypeEnum getEnumByValue(int value) {
        for (SprintPlanSourceTypeEnum e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        return null;
    }
}
