package com.chris64233.cc.satellite.web.dto;

import java.util.List;

public record StationScheduleResponse(
        String stationCode,
        List<AntennaSchedule> antennas) {

    public record AntennaSchedule(
            Long antennaId,
            String antennaCode,
            List<ContactResponse> contacts) {
    }
}
