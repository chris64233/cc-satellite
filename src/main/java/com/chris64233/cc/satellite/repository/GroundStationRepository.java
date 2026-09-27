package com.chris64233.cc.satellite.repository;

import com.chris64233.cc.satellite.domain.GroundStation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GroundStationRepository extends JpaRepository<GroundStation, String> {

    /**
     * 排程时对地面站加悲观写锁，串行化同一站点上的并发排程，避免天线时间重叠或转向间隔被破坏。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GroundStation s where s.code = :code")
    Optional<GroundStation> findByCodeForUpdate(@Param("code") String code);
}
