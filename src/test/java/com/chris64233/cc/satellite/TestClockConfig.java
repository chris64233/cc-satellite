package com.chris64233.cc.satellite;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

/** 用可变时钟替换系统时钟，便于测试“任务是否已开始”等时间相关规则。 */
@TestConfiguration
public class TestClockConfig {

    public static final Instant BASE = Instant.parse("2026-09-27T00:00:00Z");

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(BASE);
    }
}
