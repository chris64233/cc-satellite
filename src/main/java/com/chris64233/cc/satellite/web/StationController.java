package com.chris64233.cc.satellite.web;

import com.chris64233.cc.satellite.domain.GroundStation;
import com.chris64233.cc.satellite.service.CatalogService;
import com.chris64233.cc.satellite.service.SchedulingService;
import com.chris64233.cc.satellite.web.dto.CreateStationRequest;
import com.chris64233.cc.satellite.web.dto.StationScheduleResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stations")
public class StationController {

    private final CatalogService catalogService;
    private final SchedulingService schedulingService;

    public StationController(CatalogService catalogService, SchedulingService schedulingService) {
        this.catalogService = catalogService;
        this.schedulingService = schedulingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StationView create(@Valid @RequestBody CreateStationRequest request) {
        GroundStation station = catalogService.createStation(request);
        return new StationView(
                station.getCode(),
                List.copyOf(station.getSupportedBands()),
                station.getAntennas().stream()
                        .map(a -> new AntennaView(a.getId(), a.getCode(), a.getSlewSeconds()))
                        .toList());
    }

    @GetMapping("/{code}/schedule")
    public StationScheduleResponse schedule(@PathVariable String code) {
        return schedulingService.getStationSchedule(code);
    }

    public record StationView(String code, List<String> supportedBands, List<AntennaView> antennas) {
    }

    public record AntennaView(Long id, String code, long slewSeconds) {
    }
}
