package com.chris64233.cc.satellite.web;

import com.chris64233.cc.satellite.service.CatalogService;
import com.chris64233.cc.satellite.service.SchedulingService;
import com.chris64233.cc.satellite.web.dto.ContactResponse;
import com.chris64233.cc.satellite.web.dto.CreateStationRequest;
import com.chris64233.cc.satellite.web.dto.StationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
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
    public ResponseEntity<StationResponse> create(@Valid @RequestBody CreateStationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StationResponse.from(catalogService.createStation(request)));
    }

    /** 地面站日程：指定时间范围内全部已排程任务，按天线编号、开始时间排序。 */
    @GetMapping("/{code}/schedule")
    public List<ContactResponse> schedule(@PathVariable String code,
                                          @RequestParam(required = false) String from,
                                          @RequestParam(required = false) String to) {
        Instant fromInstant = from == null ? Instant.EPOCH : Instant.parse(from);
        Instant toInstant = to == null ? Instant.parse("9999-01-01T00:00:00Z") : Instant.parse(to);
        return schedulingService.stationSchedule(code, fromInstant, toInstant).stream()
                .map(ContactResponse::from)
                .toList();
    }
}
