package com.selfagent.common;

import java.lang.annotation.*;

/**
 * 标记方法包含计时日志，配合 TimingLogger 使用。
 * 实际输出受 TimingLogger 全局开关控制。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Timed {
    /** 可选描述，用于文档说明计时范围 */
    String value() default "";
}
