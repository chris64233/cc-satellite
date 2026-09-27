package com.chris64233.cc.satellite.repo;

import com.chris64233.cc.satellite.domain.GroundStation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GroundStationRepository extends JpaRepository<GroundStation, Long> {

    Optional<GroundStation> findByCode(String code);

    /** 排程时对地面站加悲观写锁，串行化同一站点的排程，保证并发下不产生重叠。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GroundStation s where s.id = :id")
    Optional<GroundStation> findByIdForUpdate(@Param("id") Long id);
}
