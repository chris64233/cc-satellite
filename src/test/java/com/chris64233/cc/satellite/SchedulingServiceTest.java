package com.chris64233.cc.satellite;

import com.chris64233.cc.satellite.domain.ContactStatus;
import com.chris64233.cc.satellite.domain.ContactTask;
import com.chris64233.cc.satellite.domain.GroundStation;
import com.chris64233.cc.satellite.domain.PassWindow;
import com.chris64233.cc.satellite.repo.ContactTaskRepository;
import com.chris64233.cc.satellite.service.CatalogService;
import com.chris64233.cc.satellite.service.ConflictException;
import com.chris64233.cc.satellite.service.NotFoundException;
import com.chris64233.cc.satellite.service.SchedulingService;
import com.chris64233.cc.satellite.service.UnschedulableException;
import com.chris64233.cc.satellite.web.dto.CreateStationRequest;
import com.chris64233.cc.satellite.web.dto.CreateWindowRequest;
import com.chris64233.cc.satellite.web.dto.ScheduleContactRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(TestClockConfig.class)
class SchedulingServiceTest {

    @Autowired
    SchedulingService schedulingService;
    @Autowired
    CatalogService catalogService;
    @Autowired
    ContactTaskRepository taskRepository;
    @Autowired
    MutableClock clock;

    static final Instant BASE = TestClockConfig.BASE;
    static final AtomicInteger ANTENNA_SEQ = new AtomicInteger(100);
    static final AtomicInteger STATION_SEQ = new AtomicInteger();
    static final AtomicInteger KEY_SEQ = new AtomicInteger();

    static Instant t(int hour, int minute) {
        return BASE.plus(Duration.ofHours(hour)).plus(Duration.ofMinutes(minute));
    }

    String key() {
        return "K-" + KEY_SEQ.incrementAndGet();
    }

    GroundStation newStation(long slewSeconds, int antennaCount) {
        List<CreateStationRequest.AntennaSpec> antennas = new ArrayList<>();
        for (int i = 0; i < antennaCount; i++) {
            antennas.add(new CreateStationRequest.AntennaSpec(ANTENNA_SEQ.incrementAndGet(), slewSeconds));
        }
        return catalogService.createStation(new CreateStationRequest(
                "GS-" + STATION_SEQ.incrementAndGet(), Set.of("S", "X"), antennas));
    }

    PassWindow newWindow(String stationCode, String satellite, Instant start, Instant end, String... bands) {
        return catalogService.createWindow(
                new CreateWindowRequest(stationCode, satellite, start, end, Set.of(bands)));
    }

    ScheduleContactRequest req(String key, Long windowId, int minutes, Instant desired) {
        return new ScheduleContactRequest(key, windowId, "S", minutes, desired);
    }

    @Test
    void placesContactAtDesiredStartInsideWindow() {
        GroundStation station = newStation(300, 2);
        PassWindow window = newWindow(station.getCode(), "SAT-A", t(10, 0), t(11, 0), "S");

        var result = schedulingService.schedule(req(key(), window.getId(), 10, t(10, 15)));

        assertTrue(result.created());
        assertEquals(t(10, 15), result.task().getStartTime());
        assertEquals(t(10, 25), result.task().getEndTime());
        assertEquals(station.getAntennas().get(0).getAntennaNumber(),
                result.task().getAntenna().getAntennaNumber());
    }

    @Test
    void clampsDesiredStartIntoWindow() {
        GroundStation station = newStation(300, 1);
        PassWindow window = newWindow(station.getCode(), "SAT-A", t(10, 0), t(11, 0), "S");

        var early = schedulingService.schedule(req(key(), window.getId(), 10, t(9, 0)));
        assertEquals(t(10, 0), early.task().getStartTime());

        var late = schedulingService.schedule(req(key(), window.getId(), 10, t(10, 55)));
        assertEquals(t(10, 50), late.task().getStartTime());
        assertEquals(t(11, 0), late.task().getEndTime());
    }

    @Test
    void rejectsIncompatibleBandAndLeavesNoOccupancy() {
        GroundStation station = newStation(300, 1);
        PassWindow window = newWindow(station.getCode(), "SAT-A", t(10, 0), t(11, 0), "S");
        long before = taskRepository.count();

        // 窗口不支持 X 频段
        assertThrows(UnschedulableException.class,
                () -> schedulingService.schedule(new ScheduleContactRequest(
                        key(), window.getId(), "X", 10, t(10, 0))));
        assertEquals(before, taskRepository.count());

        // 失败后同一位置仍可排程：没有残留占用
        var ok = schedulingService.schedule(req(key(), window.getId(), 10, t(10, 0)));
        assertEquals(t(10, 0), ok.task().getStartTime());
    }

    @Test
    void rejectsBandNotSupportedByStation() {
        GroundStation station = newStation(300, 1);
        // 窗口声明了 X 频段，但地面站只支持 S（newStation 支持 S/X，这里单独建只支持 S 的站）
        GroundStation sOnly = catalogService.createStation(new CreateStationRequest(
                "GS-S-ONLY", Set.of("S"),
                List.of(new CreateStationRequest.AntennaSpec(ANTENNA_SEQ.incrementAndGet(), 0))));
        PassWindow window = newWindow(sOnly.getCode(), "SAT-A", t(10, 0), t(11, 0), "S", "X");

        assertThrows(UnschedulableException.class,
                () -> schedulingService.schedule(new ScheduleContactRequest(
                        key(), window.getId(), "X", 10, t(10, 0))));
    }

    @Test
    void rejectsDurationLongerThanWindow() {
        GroundStation station = newStation(300, 1);
        PassWindow window = newWindow(station.getCode(), "SAT-A", t(10, 0), t(10, 30), "S");
        long before = taskRepository.count();

        assertThrows(UnschedulableException.class,
                () -> schedulingService.schedule(req(key(), window.getId(), 45, t(10, 0))));
        assertEquals(before, taskRepository.count());
    }

    @Test
    void picksFreeAntennaClosestToDesiredTime() {
        GroundStation station = newStation(300, 2);
        int a1 = station.getAntennas().get(0).getAntennaNumber();
        int a2 = station.getAntennas().get(1).getAntennaNumber();
        PassWindow window = newWindow(station.getCode(), "SAT-A", t(10, 0), t(11, 0), "S");

        var first = schedulingService.schedule(req(key(), window.getId(), 30, t(10, 0)));
        assertEquals(Math.min(a1, a2), first.task().getAntenna().getAntennaNumber());

        // 第一根天线 10:00-10:30 已占用，第二根在期望时间空闲
        var second = schedulingService.schedule(req(key(), window.getId(), 30, t(10, 0)));
        assertEquals(Math.max(a1, a2), second.task().getAntenna().getAntennaNumber());
        assertEquals(t(10, 0), second.task().getStartTime());
    }

    @Test
    void sameSatelliteAllowsBackToBack() {
        GroundStation station = newStation(300, 1);
        PassWindow window = newWindow(station.getCode(), "SAT-A", t(10, 0), t(11, 0), "S");

        schedulingService.schedule(req(key(), window.getId(), 30, t(10, 0)));
        var second = schedulingService.schedule(req(key(), window.getId(), 20, t(10, 30)));

        assertEquals(t(10, 30), second.task().getStartTime());
    }

    @Test
    void differentSatelliteRequiresSlewGapAfterPrevious() {
        GroundStation station = newStation(300, 1);
        PassWindow wa = newWindow(station.getCode(), "SAT-A", t(10, 0), t(11, 0), "S");
        PassWindow wb = newWindow(station.getCode(), "SAT-B", t(10, 0), t(11, 0), "S");

        schedulingService.schedule(req(key(), wa.getId(), 30, t(10, 0)));
        var second = schedulingService.schedule(req(key(), wb.getId(), 20, t(10, 30)));

        // SAT-A 10:00-10:30 结束后需 300 秒转向，最早 10:35 开始
        assertEquals(t(10, 35), second.task().getStartTime());
    }

    @Test
    void differentSatelliteRequiresSlewGapBeforeNext() {
        GroundStation station = newStation(300, 1);
        PassWindow wa = newWindow(station.getCode(), "SAT-A", t(10, 0), t(11, 0), "S");
        PassWindow wb = newWindow(station.getCode(), "SAT-B", t(10, 0), t(11, 0), "S");

        schedulingService.schedule(req(key(), wa.getId(), 30, t(10, 30)));
        // 期望 10:10 开始 20 分钟，但 10:30 前必须留出 300 秒转向，最晚 10:05 开始
        var second = schedulingService.schedule(req(key(), wb.getId(), 20, t(10, 10)));

        assertEquals(t(10, 5), second.task().getStartTime());
        assertEquals(t(10, 25), second.task().getEndTime());
    }

    @Test
    void tieOnDistancePrefersEarlierStart() {
        GroundStation station = newStation(0, 1);
        PassWindow window = newWindow(station.getCode(), "SAT-A", t(10, 0), t(11, 0), "S");
        // 已有任务 10:20-10:40
        schedulingService.schedule(req(key(), window.getId(), 20, t(10, 20)));

        // 期望 10:25：前间隙候选 10:10（距 15 分钟），后间隙候选 10:40（距 15 分钟），选更早的
        var result = schedulingService.schedule(req(key(), window.getId(), 10, t(10, 25)));
        assertEquals(t(10, 10), result.task().getStartTime());
    }

    @Test
    void idempotentReplayReturnsOriginalAndDifferentContentConflicts() {
        GroundStation station = newStation(300, 1);
        PassWindow window = newWindow(station.getCode(), "SAT-A", t(10, 0), t(11, 0), "S");
        long before = taskRepository.count();

        var request = req("IDEM-1", window.getId(), 20, t(10, 0));
        var first = schedulingService.schedule(request);
        assertTrue(first.created());

        var replay = schedulingService.schedule(request);
        assertFalse(replay.created());
        assertEquals(first.task().getId(), replay.task().getId());
        assertEquals(before + 1, taskRepository.count());

        var different = new ScheduleContactRequest("IDEM-1", window.getId(), "S", 30, t(10, 0));
        assertThrows(ConflictException.class, () -> schedulingService.schedule(different));
        assertEquals(before + 1, taskRepository.count());
    }

    @Test
    void cancelReleasesAntennaAndOnlyTakesEffectOnce() {
        GroundStation station = newStation(300, 1);
        PassWindow window = newWindow(station.getCode(), "SAT-A", t(10, 0), t(11, 0), "S");
        var scheduled = schedulingService.schedule(req(key(), window.getId(), 30, t(10, 0)));
        long taskId = scheduled.task().getId();

        clock.setInstant(t(9, 0));
        ContactTask cancelled = schedulingService.cancel(taskId);
        assertEquals(ContactStatus.CANCELLED, cancelled.getStatus());
        assertEquals(t(9, 0), cancelled.getCancelledAt());

        // 重复取消：幂等空操作，取消时间不变
        ContactTask again = schedulingService.cancel(taskId);
        assertEquals(ContactStatus.CANCELLED, again.getStatus());
        assertEquals(t(9, 0), again.getCancelledAt());

        // 天线已释放：同一时段可再次排程
        var rescheduled = schedulingService.schedule(req(key(), window.getId(), 30, t(10, 0)));
        assertEquals(t(10, 0), rescheduled.task().getStartTime());

        // 已开始的任务不可取消
        clock.setInstant(t(10, 0));
        long startedId = rescheduled.task().getId();
        assertThrows(ConflictException.class, () -> schedulingService.cancel(startedId));
    }

    @Test
    void stationScheduleIsOrderedAndDetailsAreQueryable() {
        GroundStation station = newStation(300, 2);
        int a1 = Math.min(station.getAntennas().get(0).getAntennaNumber(),
                station.getAntennas().get(1).getAntennaNumber());
        int a2 = Math.max(station.getAntennas().get(0).getAntennaNumber(),
                station.getAntennas().get(1).getAntennaNumber());
        PassWindow window = newWindow(station.getCode(), "SAT-A", t(10, 0), t(11, 0), "S");

        var t1 = schedulingService.schedule(req(key(), window.getId(), 30, t(10, 0)));
        var t2 = schedulingService.schedule(req(key(), window.getId(), 30, t(10, 0)));
        var t3 = schedulingService.schedule(req(key(), window.getId(), 15, t(10, 30)));

        List<ContactTask> schedule = schedulingService.stationSchedule(station.getCode(), t(9, 0), t(12, 0));
        assertEquals(3, schedule.size());
        // 按天线编号、开始时间排序：a1 10:00、a1 10:30、a2 10:00
        assertEquals(a1, schedule.get(0).getAntenna().getAntennaNumber());
        assertEquals(t(10, 0), schedule.get(0).getStartTime());
        assertEquals(a1, schedule.get(1).getAntenna().getAntennaNumber());
        assertEquals(t(10, 30), schedule.get(1).getStartTime());
        assertEquals(a2, schedule.get(2).getAntenna().getAntennaNumber());

        ContactTask details = schedulingService.contactDetails(t1.task().getId());
        assertEquals("SAT-A", details.getSatellite());
        assertEquals("S", details.getBand());
        assertEquals(ContactStatus.SCHEDULED, details.getStatus());

        assertThrows(NotFoundException.class, () -> schedulingService.contactDetails(-1L));
    }

    @Test
    void concurrentSchedulingNeverOverlapsNorViolatesSlew() throws Exception {
        GroundStation station = newStation(300, 1);
        Long antennaId = station.getAntennas().get(0).getId();
        PassWindow wa = newWindow(station.getCode(), "SAT-A", t(10, 0), t(12, 0), "S");
        PassWindow wb = newWindow(station.getCode(), "SAT-B", t(10, 0), t(12, 0), "S");

        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int idx = i;
            Long windowId = idx % 2 == 0 ? wa.getId() : wb.getId();
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    schedulingService.schedule(new ScheduleContactRequest(
                            "CONC-" + idx, windowId, "S", 15, t(10, 0)));
                    return true;
                } catch (UnschedulableException e) {
                    return false;
                }
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();

        int succeeded = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(30, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        pool.shutdown();

        List<ContactTask> tasks = taskRepository.findByAntennaAndTimeRange(
                antennaId, ContactStatus.SCHEDULED, t(10, 0), t(12, 0));
        assertEquals(succeeded, tasks.size());
        // 窗口 120 分钟，每个任务 15 分钟，即使全部需要转向也至少能排 6 个
        assertTrue(succeeded >= 6, "至少应排入 6 个任务，实际 " + succeeded);
        for (int i = 1; i < tasks.size(); i++) {
            ContactTask prev = tasks.get(i - 1);
            ContactTask cur = tasks.get(i);
            long minGapSeconds = prev.getSatellite().equals(cur.getSatellite()) ? 0 : 300;
            assertFalse(cur.getStartTime().isBefore(prev.getEndTime().plusSeconds(minGapSeconds)),
                    "任务重叠或转向间隔不足: " + prev.getId() + " -> " + cur.getId());
        }
    }
}
