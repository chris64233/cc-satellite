package com.chris64233.cc.satellite.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 测试中使用的可变 Clock，用于验证“取消未开始任务”等与当前时间相关的规则。
 */
public class MutableClock extends Clock {

    private Instant instant;
    private final ZoneId zone;

    public MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
