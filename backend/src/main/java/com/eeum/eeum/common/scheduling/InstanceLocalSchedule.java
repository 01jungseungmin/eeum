package com.eeum.eeum.common.scheduling;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 이 스케줄은 인스턴스마다 독립적으로 돌아야 한다는 표시. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InstanceLocalSchedule {
}
