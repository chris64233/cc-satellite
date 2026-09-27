package com.chris64233.cc.satellite.web;

import com.chris64233.cc.satellite.domain.VisibilityWindow;
import com.chris64233.cc.satellite.service.CatalogService;
import com.chris64233.cc.satellite.web.dto.CreateWindowRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/windows")
public class WindowController {

    private final CatalogService catalogService;

    public WindowController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WindowView create(@Valid @RequestBody CreateWindowRequest request) {
        VisibilityWindow window = catalogService.createWindow(request);
        return new WindowView(
                window.getId(),
                window.getStation().getCode(),
                window.getSatellite(),
                window.getStartTime(),
                window.getEndTime(),
                List.copyOf(window.getBands()));
    }

    public record WindowView(Long id, String stationCode, String satellite,
                             Instant startTime, Instant endTime, List<String> bands) {
    }
}
