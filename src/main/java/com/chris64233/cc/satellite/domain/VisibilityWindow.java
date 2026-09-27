package com.chris64233.cc.satellite.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 卫星过境窗口：站点、卫星、开始/结束时间与可用频段。
 */
@Entity
@Table(name = "visibility_window")
public class VisibilityWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_code", nullable = false)
    private GroundStation station;

    @Column(name = "satellite", nullable = false)
    private String satellite;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "visibility_window_band", joinColumns = @JoinColumn(name = "window_id"))
    @Column(name = "band", nullable = false)
    private Set<String> bands = new LinkedHashSet<>();

    protected VisibilityWindow() {
    }

    public VisibilityWindow(GroundStation station, String satellite, Instant startTime, Instant endTime,
                            Set<String> bands) {
        this.station = station;
        this.satellite = satellite;
        this.startTime = startTime;
        this.endTime = endTime;
        this.bands = new LinkedHashSet<>(bands);
    }

    public Long getId() {
        return id;
    }

    public GroundStation getStation() {
        return station;
    }

    public String getSatellite() {
        return satellite;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public Set<String> getBands() {
        return bands;
    }
}
