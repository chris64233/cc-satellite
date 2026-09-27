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

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 卫星过境窗口：站点、卫星、开始结束时间与可用频段。
 */
@Entity
public class PassWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private GroundStation station;

    @Column(nullable = false)
    private String satellite;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "window_bands", joinColumns = @JoinColumn(name = "window_id"))
    @Column(name = "band")
    private Set<String> bands = new LinkedHashSet<>();

    protected PassWindow() {
    }

    public PassWindow(GroundStation station, String satellite, Instant startTime, Instant endTime, Set<String> bands) {
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
