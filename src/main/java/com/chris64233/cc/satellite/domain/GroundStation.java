package com.chris64233.cc.satellite.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 地面站：唯一编号、支持频段集合以及所属天线。
 */
@Entity
public class GroundStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "station_bands", joinColumns = @JoinColumn(name = "station_id"))
    @Column(name = "band")
    private Set<String> bands = new LinkedHashSet<>();

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Antenna> antennas = new ArrayList<>();

    protected GroundStation() {
    }

    public GroundStation(String code, Set<String> bands) {
        this.code = code;
        this.bands = new LinkedHashSet<>(bands);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Set<String> getBands() {
        return bands;
    }

    public List<Antenna> getAntennas() {
        return antennas;
    }

    public void addAntenna(Antenna antenna) {
        antennas.add(antenna);
        antenna.setStation(this);
    }
}
