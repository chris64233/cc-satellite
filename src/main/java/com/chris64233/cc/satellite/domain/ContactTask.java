package com.chris64233.cc.satellite.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.time.Instant;
import java.util.Objects;

/**
 * 联系任务：一次已排程的卫星联系，占用某根天线的一段时间。
 * 同时保存幂等键与请求内容，用于幂等重放与冲突检测。
 */
@Entity
public class ContactTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "window_id")
    private PassWindow window;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private GroundStation station;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "antenna_id")
    private Antenna antenna;

    @Column(nullable = false)
    private String satellite;

    @Column(nullable = false)
    private String band;

    @Column(nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private Instant desiredStart;

    @Column(nullable = false)
    private Instant startTime;

    @Column(nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContactStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant cancelledAt;

    protected ContactTask() {
    }

    public ContactTask(String idempotencyKey, PassWindow window, GroundStation station, Antenna antenna,
                       String band, int durationMinutes, Instant desiredStart,
                       Instant startTime, Instant endTime, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.window = window;
        this.station = station;
        this.antenna = antenna;
        this.satellite = window.getSatellite();
        this.band = band;
        this.durationMinutes = durationMinutes;
        this.desiredStart = desiredStart;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = ContactStatus.SCHEDULED;
        this.createdAt = createdAt;
    }

    /** 判断请求内容是否与创建本任务时的内容一致（用于幂等键冲突检测）。 */
    public boolean matchesRequest(Long windowId, String band, int durationMinutes, Instant desiredStart) {
        return Objects.equals(window.getId(), windowId)
                && this.band.equals(band)
                && this.durationMinutes == durationMinutes
                && this.desiredStart.equals(desiredStart);
    }

    public void cancel(Instant cancelledAt) {
        this.status = ContactStatus.CANCELLED;
        this.cancelledAt = cancelledAt;
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public PassWindow getWindow() {
        return window;
    }

    public GroundStation getStation() {
        return station;
    }

    public Antenna getAntenna() {
        return antenna;
    }

    public String getSatellite() {
        return satellite;
    }

    public String getBand() {
        return band;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public Instant getDesiredStart() {
        return desiredStart;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public ContactStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
