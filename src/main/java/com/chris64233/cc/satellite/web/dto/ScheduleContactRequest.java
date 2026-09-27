package com.chris64233.cc.satellite.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ScheduleContactRequest(
        @NotBlank String idempotencyKey,
        @NotNull Long windowId,
        @NotBlank String band,
        @Min(1) int durationMinutes,
        @NotNull Instant desiredStart) {
}
