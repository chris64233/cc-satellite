package com.chris64233.cc.satellite.repository;

import com.chris64233.cc.satellite.domain.Contact;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByIdempotencyKey(String idempotencyKey);

    /**
     * 取消时对任务行加悲观写锁，保证“取消只生效一次”。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contact c where c.id = :id")
    Optional<Contact> findByIdForUpdate(@Param("id") Long id);

    /**
     * 查询某天线在 [from, to) 区间内仍占用天线的任务（未取消），按开始时间升序。
     * 调用方会把区间向两侧各放宽一个转向时长，以覆盖窗口边缘的转向约束。
     */
    @Query("select c from Contact c where c.antenna.id = :antennaId and c.status = 'SCHEDULED'"
            + " and c.endTime > :from and c.startTime < :to order by c.startTime asc, c.id asc")
    List<Contact> findScheduledOverlapping(@Param("antennaId") Long antennaId,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to);

    @Query("select c from Contact c where c.antenna.station.code = :stationCode and c.status = 'SCHEDULED'"
            + " order by c.antenna.id asc, c.startTime asc, c.id asc")
    List<Contact> findScheduledByStation(@Param("stationCode") String stationCode);
}
