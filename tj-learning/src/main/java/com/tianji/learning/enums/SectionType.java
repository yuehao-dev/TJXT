package com.tianji.learning.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.tianji.common.enums.BaseEnum;
import lombok.Getter;

@Getter
public enum SectionType implements BaseEnum {
    VIDEO(1, "视频"),
    EXAM(2, "考试"),
    ;
    @JsonValue
    //JsonValue 注解用于指定枚举常量的 JSON 表示值。
    @EnumValue
    //EnumValue 注解用于指定枚举常量的数据库值。
    int value;
    String desc;

    SectionType(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }


    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    //JsonCreator 注解用于指定 JSON 字符串转换为枚举常量的工厂方法。
    //DELEGATING 模式表示使用枚举常量的构造方法来创建枚举常量。
    //如果构造方法只有一个参数，该参数的类型必须是枚举常量的类型。
    public static SectionType of(Integer value){
        if (value == null) {
            return null;
        }
        for (SectionType status : values()) {
            if (status.equalsValue(value)) {
                return status;
            }
        }
        return null;
    }
}
