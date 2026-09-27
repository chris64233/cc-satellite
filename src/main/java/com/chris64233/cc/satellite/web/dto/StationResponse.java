package com.chris64233.cc.satellite.web.dto;

import com.chris64233.cc.satellite.domain.Antenna;
import com.chris64233.cc.satellite.domain.GroundStation;

import java.util.List;
import java.util.Set;

public record StationResponse(String code, Set<String> bands, List<AntennaView> antennas) {

    public record AntennaView(int antennaNumber, long slewSeconds) {
    }

    public static StationResponse from(GroundStation station) {
        List<AntennaView> antennas = station.getAntennas().stream()
                .map(a -> new AntennaView(a.getAntennaNumber(), a.getSlewSeconds()))
                .toList();
        return new StationResponse(station.getCode(), station.getBands(), antennas);
    }
}
