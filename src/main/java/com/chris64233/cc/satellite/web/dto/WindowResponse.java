package com.chris64233.cc.satellite.web.dto;

import com.chris64233.cc.satellite.domain.PassWindow;

import java.time.Instant;
import java.util.Set;

public record WindowResponse(
        long id,
        String stationCode,
        String satellite,
        Instant startTime,
        Instant endTime,
        Set<String> bands) {

    public static WindowResponse from(PassWindow window) {
        return new WindowResponse(
                window.getId(),
                window.getStation().getCode(),
                window.getSatellite(),
                window.getStartTime(),
                window.getEndTime(),
                window.getBands());
    }
}
