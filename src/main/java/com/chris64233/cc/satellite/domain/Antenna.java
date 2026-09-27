package com.chris64233.cc.satellite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 天线：属于一个地面站，定义从一颗卫星转向另一颗卫星所需的固定准备时长（秒）。
 */
@Entity
@Table(name = "antenna")
public class Antenna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_code", nullable = false)
    private GroundStation station;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "slew_seconds", nullable = false)
    private long slewSeconds;

    protected Antenna() {
    }

    public Antenna(GroundStation station, String code, long slewSeconds) {
        this.station = station;
        this.code = code;
        this.slewSeconds = slewSeconds;
    }

    public Long getId() {
        return id;
    }

    public GroundStation getStation() {
        return station;
    }

    public String getCode() {
        return code;
    }

    public long getSlewSeconds() {
        return slewSeconds;
    }
}
