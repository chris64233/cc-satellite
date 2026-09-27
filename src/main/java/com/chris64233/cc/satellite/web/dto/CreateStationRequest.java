package com.chris64233.cc.satellite.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

public record CreateStationRequest(
        @NotBlank String code,
        @NotEmpty Set<String> bands,
        @NotEmpty List<@Valid AntennaSpec> antennas) {

    public record AntennaSpec(
            @NotNull Integer antennaNumber,
            @Min(0) long slewSeconds) {
    }
}
