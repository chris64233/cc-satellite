package com.chris64233.cc.satellite.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ScheduleContactRequest(
        @NotBlank String idempotencyKey,
        @NotNull Long windowId,
        @NotBlank String band,
        @Min(1) long durationMinutes,
        @NotNull Instant desiredStart) {

    /**
     * 请求内容指纹：同一幂等键重放时用于判断内容是否一致。
     */
    public String contentHash() {
        return windowId + "|" + band + "|" + durationMinutes + "|" + desiredStart;
    }
}
