package com.chris64233.cc.satellite.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Set;

public record CreateWindowRequest(
        @NotBlank String stationCode,
        @NotBlank String satellite,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotEmpty Set<@NotBlank String> bands) {
}
