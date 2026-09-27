package com.chris64233.cc.satellite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

/**
 * 天线：拥有全局唯一编号，并定义从一个卫星转向另一卫星所需的固定准备时长（秒）。
 */
@Entity
public class Antenna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private int antennaNumber;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private GroundStation station;

    /** 转向准备时长（秒）：相邻任务属于不同卫星时必须留出的间隔。 */
    @Column(nullable = false)
    private long slewSeconds;

    protected Antenna() {
    }

    public Antenna(int antennaNumber, long slewSeconds) {
        this.antennaNumber = antennaNumber;
        this.slewSeconds = slewSeconds;
    }

    public Long getId() {
        return id;
    }

    public int getAntennaNumber() {
        return antennaNumber;
    }

    public GroundStation getStation() {
        return station;
    }

    void setStation(GroundStation station) {
        this.station = station;
    }

    public long getSlewSeconds() {
        return slewSeconds;
    }
}
