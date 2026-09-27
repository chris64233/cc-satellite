package com.chris64233.cc.satellite.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Set;

public record CreateStationRequest(
        @NotBlank String code,
        @NotEmpty Set<@NotBlank String> supportedBands,
        @NotEmpty List<@Valid AntennaSpec> antennas) {

    public record AntennaSpec(
            @NotBlank String code,
            @Min(0) long slewSeconds) {
    }
}
