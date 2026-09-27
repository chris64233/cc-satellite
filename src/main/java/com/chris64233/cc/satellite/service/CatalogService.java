package com.chris64233.cc.satellite.service;

import com.chris64233.cc.satellite.domain.GroundStation;
import com.chris64233.cc.satellite.domain.VisibilityWindow;
import com.chris64233.cc.satellite.repository.GroundStationRepository;
import com.chris64233.cc.satellite.repository.VisibilityWindowRepository;
import com.chris64233.cc.satellite.service.error.ConflictException;
import com.chris64233.cc.satellite.service.error.NotFoundException;
import com.chris64233.cc.satellite.web.dto.CreateStationRequest;
import com.chris64233.cc.satellite.web.dto.CreateWindowRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 地面站与过境窗口的登记。
 */
@Service
public class CatalogService {

    private final GroundStationRepository stationRepository;
    private final VisibilityWindowRepository windowRepository;

    public CatalogService(GroundStationRepository stationRepository,
                          VisibilityWindowRepository windowRepository) {
        this.stationRepository = stationRepository;
        this.windowRepository = windowRepository;
    }

    @Transactional
    public GroundStation createStation(CreateStationRequest request) {
        if (stationRepository.existsById(request.code())) {
            throw new ConflictException("地面站编号已存在: " + request.code());
        }
        GroundStation station = new GroundStation(request.code(), request.supportedBands());
        request.antennas().forEach(spec -> station.addAntenna(spec.code(), spec.slewSeconds()));
        return stationRepository.save(station);
    }

    @Transactional
    public VisibilityWindow createWindow(CreateWindowRequest request) {
        GroundStation station = stationRepository.findById(request.stationCode())
                .orElseThrow(() -> new NotFoundException("地面站不存在: " + request.stationCode()));
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("窗口结束时间必须晚于开始时间");
        }
        return windowRepository.save(new VisibilityWindow(
                station, request.satellite(), request.startTime(), request.endTime(), request.bands()));
    }
}
