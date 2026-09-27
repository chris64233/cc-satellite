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
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 联系任务：在一次过境窗口内占用某根天线的一段排程。
 */
@Entity
@Table(name = "contact")
public class Contact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
    private String idempotencyKey;

    /**
     * 请求内容指纹，用于幂等重放时识别“同键不同内容”的冲突。
     */
    @Column(name = "request_hash", nullable = false, updatable = false)
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "window_id", nullable = false)
    private VisibilityWindow window;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "antenna_id", nullable = false)
    private Antenna antenna;

    @Column(name = "satellite", nullable = false)
    private String satellite;

    @Column(name = "band", nullable = false)
    private String band;

    @Column(name = "duration_minutes", nullable = false)
    private long durationMinutes;

    @Column(name = "desired_start", nullable = false)
    private Instant desiredStart;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContactStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Contact() {
    }

    public Contact(String idempotencyKey, String requestHash, VisibilityWindow window, Antenna antenna,
                   String band, long durationMinutes, Instant desiredStart, Instant startTime, Instant endTime,
                   Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.window = window;
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

    public String getRequestHash() {
        return requestHash;
    }

    public VisibilityWindow getWindow() {
        return window;
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

    public long getDurationMinutes() {
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
