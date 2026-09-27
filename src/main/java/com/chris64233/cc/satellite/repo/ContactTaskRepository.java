package com.chris64233.cc.satellite.repo;

import com.chris64233.cc.satellite.domain.ContactStatus;
import com.chris64233.cc.satellite.domain.ContactTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ContactTaskRepository extends JpaRepository<ContactTask, Long> {

    Optional<ContactTask> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ContactTask t where t.id = :id")
    Optional<ContactTask> findByIdForUpdate(@Param("id") Long id);

    @Query("select t from ContactTask t join fetch t.antenna join fetch t.station join fetch t.window "
            + "where t.id = :id")
    Optional<ContactTask> findDetailedById(@Param("id") Long id);

    /** 查询天线在指定时间段内的占用（含相邻任务，用于转向间隔检查）。 */
    @Query("select t from ContactTask t join fetch t.antenna where t.antenna.id = :antennaId and t.status = :status "
            + "and t.endTime > :from and t.startTime < :to order by t.startTime")
    List<ContactTask> findByAntennaAndTimeRange(@Param("antennaId") Long antennaId,
                                                @Param("status") ContactStatus status,
                                                @Param("from") Instant from,
                                                @Param("to") Instant to);

    /** 地面站日程：按天线编号、开始时间排序。 */
    @Query("select t from ContactTask t join fetch t.antenna join fetch t.station join fetch t.window "
            + "where t.station.id = :stationId and t.status = :status "
            + "and t.endTime > :from and t.startTime < :to "
            + "order by t.antenna.antennaNumber, t.startTime")
    List<ContactTask> findStationSchedule(@Param("stationId") Long stationId,
                                          @Param("status") ContactStatus status,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to);
}
