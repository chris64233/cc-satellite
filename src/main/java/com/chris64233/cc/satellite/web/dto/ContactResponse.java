package com.chris64233.cc.satellite.web.dto;

import com.chris64233.cc.satellite.domain.ContactTask;

import java.time.Instant;

public record ContactResponse(
        long id,
        String idempotencyKey,
        long windowId,
        String stationCode,
        int antennaNumber,
        String satellite,
        String band,
        int durationMinutes,
        Instant desiredStart,
        Instant startTime,
        Instant endTime,
        String status,
        Instant createdAt,
        Instant cancelledAt) {

    public static ContactResponse from(ContactTask task) {
        return new ContactResponse(
                task.getId(),
                task.getIdempotencyKey(),
                task.getWindow().getId(),
                task.getStation().getCode(),
                task.getAntenna().getAntennaNumber(),
                task.getSatellite(),
                task.getBand(),
                task.getDurationMinutes(),
                task.getDesiredStart(),
                task.getStartTime(),
                task.getEndTime(),
                task.getStatus().name(),
                task.getCreatedAt(),
                task.getCancelledAt());
    }
}
