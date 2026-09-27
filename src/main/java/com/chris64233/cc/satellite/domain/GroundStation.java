package com.chris64233.cc.satellite.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 地面站：唯一编号 + 支持频段 + 天线列表。
 */
@Entity
@Table(name = "ground_station")
public class GroundStation {

    @Id
    @Column(name = "code", nullable = false, updatable = false)
    private String code;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "ground_station_band", joinColumns = @JoinColumn(name = "station_code"))
    @Column(name = "band", nullable = false)
    private Set<String> supportedBands = new LinkedHashSet<>();

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Antenna> antennas = new ArrayList<>();

    protected GroundStation() {
    }

    public GroundStation(String code, Set<String> supportedBands) {
        this.code = code;
        this.supportedBands = new LinkedHashSet<>(supportedBands);
    }

    public Antenna addAntenna(String antennaCode, long slewSeconds) {
        Antenna antenna = new Antenna(this, antennaCode, slewSeconds);
        antennas.add(antenna);
        return antenna;
    }

    public String getCode() {
        return code;
    }

    public Set<String> getSupportedBands() {
        return supportedBands;
    }

    public List<Antenna> getAntennas() {
        return antennas;
    }
}
