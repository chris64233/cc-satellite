package com.chris64233.cc.satellite.web.dto;

import com.chris64233.cc.satellite.domain.Contact;
import com.chris64233.cc.satellite.domain.ContactStatus;

import java.time.Instant;

public record ContactResponse(
        Long id,
        String idempotencyKey,
        Long windowId,
        String stationCode,
        Long antennaId,
        String antennaCode,
        String satellite,
        String band,
        long durationMinutes,
        Instant desiredStart,
        Instant startTime,
        Instant endTime,
        ContactStatus status,
        Instant createdAt,
        Instant cancelledAt) {

    public static ContactResponse from(Contact contact) {
        return new ContactResponse(
                contact.getId(),
                contact.getIdempotencyKey(),
                contact.getWindow().getId(),
                contact.getAntenna().getStation().getCode(),
                contact.getAntenna().getId(),
                contact.getAntenna().getCode(),
                contact.getSatellite(),
                contact.getBand(),
                contact.getDurationMinutes(),
                contact.getDesiredStart(),
                contact.getStartTime(),
                contact.getEndTime(),
                contact.getStatus(),
                contact.getCreatedAt(),
                contact.getCancelledAt());
    }
}
