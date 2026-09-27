package com.chris64233.cc.satellite.service;

import com.chris64233.cc.satellite.domain.Antenna;
import com.chris64233.cc.satellite.domain.GroundStation;
import com.chris64233.cc.satellite.domain.PassWindow;
import com.chris64233.cc.satellite.repo.GroundStationRepository;
import com.chris64233.cc.satellite.repo.PassWindowRepository;
import com.chris64233.cc.satellite.web.dto.CreateStationRequest;
import com.chris64233.cc.satellite.web.dto.CreateWindowRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 地面站与过境窗口的登记服务。
 */
@Service
public class CatalogService {

    private final GroundStationRepository stationRepository;
    private final PassWindowRepository windowRepository;

    public CatalogService(GroundStationRepository stationRepository,
                          PassWindowRepository windowRepository) {
        this.stationRepository = stationRepository;
        this.windowRepository = windowRepository;
    }

    @Transactional
    public GroundStation createStation(CreateStationRequest request) {
        stationRepository.findByCode(request.code()).ifPresent(s -> {
            throw new ConflictException("地面站编号已存在: " + request.code());
        });
        GroundStation station = new GroundStation(request.code(), request.bands());
        for (CreateStationRequest.AntennaSpec spec : request.antennas()) {
            station.addAntenna(new Antenna(spec.antennaNumber(), spec.slewSeconds()));
        }
        return stationRepository.save(station);
    }

    @Transactional
    public PassWindow createWindow(CreateWindowRequest request) {
        GroundStation station = stationRepository.findByCode(request.stationCode())
                .orElseThrow(() -> new NotFoundException("地面站不存在: " + request.stationCode()));
        if (!request.endTime().isAfter(request.startTime())) {
            throw new ConflictException("过境窗口结束时间必须晚于开始时间");
        }
        return windowRepository.save(new PassWindow(
                station, request.satellite(), request.startTime(), request.endTime(), request.bands()));
    }
}
