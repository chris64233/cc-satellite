package com.chris64233.cc.satellite.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.time.ZoneOffset;

@TestConfiguration
public class TestClockConfiguration {

    public static final Instant INITIAL_INSTANT = Instant.parse("2026-06-01T08:00:00Z");

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(INITIAL_INSTANT, ZoneOffset.UTC);
    }
}
