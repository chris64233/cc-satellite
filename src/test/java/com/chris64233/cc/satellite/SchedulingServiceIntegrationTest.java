package com.chris64233.cc.satellite;

import com.chris64233.cc.satellite.domain.Contact;
import com.chris64233.cc.satellite.domain.ContactStatus;
import com.chris64233.cc.satellite.domain.GroundStation;
import com.chris64233.cc.satellite.domain.VisibilityWindow;
import com.chris64233.cc.satellite.repository.ContactRepository;
import com.chris64233.cc.satellite.repository.GroundStationRepository;
import com.chris64233.cc.satellite.repository.VisibilityWindowRepository;
import com.chris64233.cc.satellite.service.CatalogService;
import com.chris64233.cc.satellite.service.SchedulingService;
import com.chris64233.cc.satellite.service.error.ConflictException;
import com.chris64233.cc.satellite.service.error.NotFoundException;
import com.chris64233.cc.satellite.service.error.UnschedulableException;
import com.chris64233.cc.satellite.support.MutableClock;
import com.chris64233.cc.satellite.support.TestClockConfiguration;
import com.chris64233.cc.satellite.web.dto.ContactResponse;
import com.chris64233.cc.satellite.web.dto.CreateStationRequest;
import com.chris64233.cc.satellite.web.dto.CreateWindowRequest;
import com.chris64233.cc.satellite.web.dto.ScheduleContactRequest;
import com.chris64233.cc.satellite.web.dto.StationScheduleResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestClockConfiguration.class)
class SchedulingServiceIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-06-01T10:00:00Z");

    @Autowired
    private CatalogService catalogService;
    @Autowired
    private SchedulingService schedulingService;
    @Autowired
    private ContactRepository contactRepository;
    @Autowired
    private GroundStationRepository stationRepository;
    @Autowired
    private VisibilityWindowRepository windowRepository;
    @Autowired
    private MutableClock clock;

    private int stationSeq;

    @BeforeEach
    void cleanUp() {
        contactRepository.deleteAll();
        windowRepository.deleteAll();
        stationRepository.deleteAll();
        clock.setInstant(TestClockConfiguration.INITIAL_INSTANT);
    }

    private GroundStation newStation(long slewSeconds, int antennaCount, String... bands) {
        List<CreateStationRequest.AntennaSpec> antennas = new ArrayList<>();
        for (int i = 1; i <= antennaCount; i++) {
            antennas.add(new CreateStationRequest.AntennaSpec("ANT-" + i, slewSeconds));
        }
        return catalogService.createStation(new CreateStationRequest(
                "ST-" + (++stationSeq), Set.of(bands), antennas));
    }

    private VisibilityWindow newWindow(GroundStation station, String satellite,
                                       Instant start, Instant end, String... bands) {
        return catalogService.createWindow(new CreateWindowRequest(
                station.getCode(), satellite, start, end, Set.of(bands)));
    }

    private ScheduleContactRequest request(String key, VisibilityWindow window, long minutes, Instant desired) {
        return new ScheduleContactRequest(key, window.getId(), "X", minutes, desired);
    }

    @Test
    void schedulesAtDesiredStartOnSmallestAntennaWhenFree() {
        GroundStation station = newStation(300, 2, "X");
        VisibilityWindow window = newWindow(station, "SAT-1", T0, T0.plusSeconds(3600), "X");

        ContactResponse response = schedulingService.schedule(request("k1", window, 30, T0.plusSeconds(900)));

        assertThat(response.startTime()).isEqualTo(T0.plusSeconds(900));
        assertThat(response.endTime()).isEqualTo(T0.plusSeconds(2700));
        assertThat(response.antennaId()).isEqualTo(station.getAntennas().get(0).getId());
        assertThat(response.satellite()).isEqualTo("SAT-1");
        assertThat(response.status()).isEqualTo(ContactStatus.SCHEDULED);
    }

    @Test
    void clampsDesiredStartIntoWindow() {
        GroundStation station = newStation(300, 1, "X");
        VisibilityWindow window = newWindow(station, "SAT-1", T0, T0.plusSeconds(3600), "X");

        ContactResponse early = schedulingService.schedule(request("k-early", window, 30, T0.minusSeconds(3600)));
        assertThat(early.startTime()).isEqualTo(T0);

        ContactResponse late = schedulingService.schedule(request("k-late", window, 30, T0.plusSeconds(7200)));
        assertThat(late.startTime()).isEqualTo(T0.plusSeconds(1800));
        assertThat(late.endTime()).isEqualTo(T0.plusSeconds(3600));
    }

    @Test
    void rejectsBandNotSupportedByWindowOrStation() {
        GroundStation station = newStation(300, 1, "X");
        VisibilityWindow window = newWindow(station, "SAT-1", T0, T0.plusSeconds(3600), "X");
        assertThatThrownBy(() -> schedulingService.schedule(
                new ScheduleContactRequest("k1", window.getId(), "S", 30, T0)))
                .isInstanceOf(UnschedulableException.class);

        // 窗口支持但站点不支持
        VisibilityWindow wideWindow = newWindow(station, "SAT-1", T0, T0.plusSeconds(3600), "X", "S");
        assertThatThrownBy(() -> schedulingService.schedule(
                new ScheduleContactRequest("k2", wideWindow.getId(), "S", 30, T0)))
                .isInstanceOf(UnschedulableException.class);
    }

    @Test
    void rejectsWhenDurationExceedsWindow() {
        GroundStation station = newStation(300, 1, "X");
        VisibilityWindow window = newWindow(station, "SAT-1", T0, T0.plusSeconds(3600), "X");
        assertThatThrownBy(() -> schedulingService.schedule(request("k1", window, 90, T0)))
                .isInstanceOf(UnschedulableException.class);
    }

    @Test
    void sameSatelliteContactsMayBeBackToBack() {
        GroundStation station = newStation(600, 1, "X");
        VisibilityWindow window = newWindow(station, "SAT-1", T0, T0.plusSeconds(7200), "X");

        schedulingService.schedule(request("k1", window, 30, T0));
        ContactResponse second = schedulingService.schedule(request("k2", window, 30, T0.plusSeconds(900)));

        // 同一卫星无需转向，可以首尾相接
        assertThat(second.startTime()).isEqualTo(T0.plusSeconds(1800));
    }

    @Test
    void enforcesSlewBetweenDifferentSatellites() {
        GroundStation station = newStation(600, 1, "X");
        VisibilityWindow w1 = newWindow(station, "SAT-1", T0, T0.plusSeconds(3600), "X");
        VisibilityWindow w2 = newWindow(station, "SAT-2", T0, T0.plusSeconds(7200), "X");

        schedulingService.schedule(request("k1", w1, 30, T0));
        ContactResponse second = schedulingService.schedule(request("k2", w2, 30, T0.plusSeconds(1800)));

        // 不同卫星：间隔至少 600 秒转向时长
        assertThat(second.startTime()).isEqualTo(T0.plusSeconds(1800 + 600));
    }

    @Test
    void slewAppliesToNeighbourEndingBeforeWindowStart() {
        GroundStation station = newStation(600, 1, "X");
        VisibilityWindow w1 = newWindow(station, "SAT-1", T0.minusSeconds(3600), T0, "X");
        VisibilityWindow w2 = newWindow(station, "SAT-2", T0.plusSeconds(300), T0.plusSeconds(3600), "X");

        schedulingService.schedule(request("k1", w1, 30, T0.minusSeconds(1800)));
        ContactResponse second = schedulingService.schedule(request("k2", w2, 30, T0.plusSeconds(300)));

        // 前一任务在窗口开始前结束，但转向约束仍然生效
        assertThat(second.startTime()).isEqualTo(T0.plusSeconds(600));
    }

    @Test
    void checksBothAdjacentTasks() {
        GroundStation station = newStation(600, 1, "X");
        VisibilityWindow window = newWindow(station, "SAT-1", T0, T0.plusSeconds(7200), "X");
        schedulingService.schedule(request("k1", window, 30, T0));            // 10:00-10:30
        schedulingService.schedule(request("k2", window, 30, T0.plusSeconds(3600))); // 11:00-11:30

        // 同一卫星恰好嵌入中间空隙，前后均首尾相接
        ContactResponse middle = schedulingService.schedule(request("k3", window, 30, T0.plusSeconds(2700)));
        assertThat(middle.startTime()).isEqualTo(T0.plusSeconds(1800));

        // 不同卫星需要前后各 600 秒转向，空隙不足
        VisibilityWindow other = newWindow(station, "SAT-2", T0, T0.plusSeconds(7200), "X");
        assertThatThrownBy(() -> schedulingService.schedule(request("k4", other, 30, T0.plusSeconds(2700))))
                .isInstanceOf(UnschedulableException.class);
    }

    @Test
    void tieBreaksByEarlierStartThenSmallerAntenna() {
        // 距离期望时间相同 -> 开始更早者优先
        GroundStation station = newStation(0, 2, "X");
        Long b1 = station.getAntennas().get(0).getId();
        Long b2 = station.getAntennas().get(1).getId();
        VisibilityWindow window = newWindow(station, "SAT-1", T0.minusSeconds(3600), T0.plusSeconds(7200), "X");

        schedulingService.schedule(request("k1", window, 70, T0.minusSeconds(3600)));   // B1: 09:00-10:10
        schedulingService.schedule(request("k2", window, 30, T0.plusSeconds(2400)));    // B1: 10:40-11:10
        schedulingService.schedule(request("k3", window, 31, T0.plusSeconds(1200)));    // B1 空隙不足 -> B2: 10:20-10:51

        // B1 候选开始 10:10（距期望 10 分钟），B2 候选开始 09:50（距期望 10 分钟）-> 更早的 09:50 胜出
        ContactResponse result = schedulingService.schedule(request("k4", window, 30, T0));
        assertThat(result.antennaId()).isEqualTo(b2);
        assertThat(result.startTime()).isEqualTo(T0.minusSeconds(600));

        // 开始时间相同 -> 天线编号更小者优先
        ContactResponse first = schedulingService.schedule(request("k5", window, 10, T0.plusSeconds(5400)));
        assertThat(first.antennaId()).isEqualTo(b1);
    }

    @Test
    void idempotentReplayReturnsOriginalAndDifferentContentConflicts() {
        GroundStation station = newStation(300, 1, "X");
        VisibilityWindow window = newWindow(station, "SAT-1", T0, T0.plusSeconds(3600), "X");

        ContactResponse first = schedulingService.schedule(request("same-key", window, 30, T0));
        ContactResponse replay = schedulingService.schedule(request("same-key", window, 30, T0));

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(contactRepository.count()).isEqualTo(1);

        assertThatThrownBy(() -> schedulingService.schedule(request("same-key", window, 45, T0)))
                .isInstanceOf(ConflictException.class);
        assertThat(contactRepository.count()).isEqualTo(1);
    }

    @Test
    void failedSchedulingLeavesNoOccupation() {
        GroundStation station = newStation(300, 1, "X");
        VisibilityWindow window = newWindow(station, "SAT-1", T0, T0.plusSeconds(3600), "X");

        schedulingService.schedule(request("k1", window, 60, T0));
        assertThatThrownBy(() -> schedulingService.schedule(request("k2", window, 30, T0)))
                .isInstanceOf(UnschedulableException.class);

        assertThat(contactRepository.findScheduledByStation(station.getCode())).hasSize(1);
        assertThat(contactRepository.count()).isEqualTo(1);
    }

    @Test
    void cancelReleasesAntennaAndOnlyTakesEffectOnce() {
        GroundStation station = newStation(300, 1, "X");
        VisibilityWindow window = newWindow(station, "SAT-1", T0, T0.plusSeconds(3600), "X");
        ContactResponse contact = schedulingService.schedule(request("k1", window, 60, T0));

        ContactResponse cancelled = schedulingService.cancel(contact.id());
        assertThat(cancelled.status()).isEqualTo(ContactStatus.CANCELLED);
        assertThat(cancelled.cancelledAt()).isEqualTo(TestClockConfiguration.INITIAL_INSTANT);

        // 重复取消：状态不变，取消时间不被覆盖
        clock.setInstant(TestClockConfiguration.INITIAL_INSTANT.plusSeconds(1800));
        ContactResponse again = schedulingService.cancel(contact.id());
        assertThat(again.status()).isEqualTo(ContactStatus.CANCELLED);
        assertThat(again.cancelledAt()).isEqualTo(TestClockConfiguration.INITIAL_INSTANT);

        // 天线已释放，同一时段可以再次排程
        ContactResponse rescheduled = schedulingService.schedule(request("k2", window, 60, T0));
        assertThat(rescheduled.startTime()).isEqualTo(T0);
    }

    @Test
    void cannotCancelStartedContact() {
        GroundStation station = newStation(300, 1, "X");
        VisibilityWindow window = newWindow(station, "SAT-1", T0, T0.plusSeconds(3600), "X");
        ContactResponse contact = schedulingService.schedule(request("k1", window, 60, T0));

        clock.setInstant(T0);
        assertThatThrownBy(() -> schedulingService.cancel(contact.id()))
                .isInstanceOf(ConflictException.class);
        assertThat(schedulingService.getContact(contact.id()).status()).isEqualTo(ContactStatus.SCHEDULED);
    }

    @Test
    void stationScheduleAndContactDetailQueries() {
        GroundStation station = newStation(300, 2, "X");
        VisibilityWindow window = newWindow(station, "SAT-1", T0, T0.plusSeconds(3600), "X");
        ContactResponse c1 = schedulingService.schedule(request("k1", window, 30, T0));
        ContactResponse c2 = schedulingService.schedule(request("k2", window, 30, T0.plusSeconds(1800)));

        StationScheduleResponse schedule = schedulingService.getStationSchedule(station.getCode());
        assertThat(schedule.stationCode()).isEqualTo(station.getCode());
        assertThat(schedule.antennas()).hasSize(2);
        long total = schedule.antennas().stream().mapToLong(a -> a.contacts().size()).sum();
        assertThat(total).isEqualTo(2);

        ContactResponse detail = schedulingService.getContact(c1.id());
        assertThat(detail.idempotencyKey()).isEqualTo("k1");
        assertThat(detail.windowId()).isEqualTo(window.getId());
        assertThat(schedulingService.getContact(c2.id()).startTime()).isEqualTo(T0.plusSeconds(1800));

        assertThatThrownBy(() -> schedulingService.getContact(999999L))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> schedulingService.getStationSchedule("NO-SUCH-STATION"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void concurrentSchedulingNeverOverlaps() throws Exception {
        GroundStation station = newStation(300, 1, "X");
        // 60 分钟窗口、单天线、每个任务 10 分钟：容量恰好 6 个
        VisibilityWindow window = newWindow(station, "SAT-1", T0, T0.plusSeconds(3600), "X");

        int threads = 8;
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String key = "concurrent-" + i;
                tasks.add(() -> {
                    try {
                        schedulingService.schedule(request(key, window, 10, T0));
                        succeeded.incrementAndGet();
                    } catch (UnschedulableException e) {
                        rejected.incrementAndGet();
                    }
                    return null;
                });
            }
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(task));
            }
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(succeeded.get()).isEqualTo(6);
        assertThat(rejected.get()).isEqualTo(2);

        List<Contact> contacts = contactRepository.findScheduledByStation(station.getCode());
        assertThat(contacts).hasSize(6);
        assertNoOverlapAndSlewRespected(contacts, 300);
    }

    @Test
    void concurrentMixedSatellitesRespectSlew() throws Exception {
        GroundStation station = newStation(120, 1, "X");
        VisibilityWindow w1 = newWindow(station, "SAT-1", T0, T0.plusSeconds(3600), "X");
        VisibilityWindow w2 = newWindow(station, "SAT-2", T0, T0.plusSeconds(3600), "X");

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                VisibilityWindow window = i % 2 == 0 ? w1 : w2;
                String key = "mixed-" + i;
                futures.add(pool.submit(() -> {
                    try {
                        schedulingService.schedule(request(key, window, 10, T0));
                    } catch (UnschedulableException ignored) {
                        // 容量不足时允许失败，但不能留下违规占用
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        List<Contact> contacts = contactRepository.findScheduledByStation(station.getCode());
        assertThat(contacts).isNotEmpty();
        assertNoOverlapAndSlewRespected(contacts, 120);
    }

    private void assertNoOverlapAndSlewRespected(List<Contact> contacts, long slewSeconds) {
        for (int i = 0; i < contacts.size(); i++) {
            Contact current = contacts.get(i);
            assertThat(current.getEndTime()).isAfter(current.getStartTime());
            if (i == 0) {
                continue;
            }
            Contact previous = contacts.get(i - 1);
            assertThat(previous.getEndTime()).isBeforeOrEqualTo(current.getStartTime());
            if (!previous.getSatellite().equals(current.getSatellite())) {
                assertThat(Duration.between(previous.getEndTime(), current.getStartTime()).getSeconds())
                        .isGreaterThanOrEqualTo(slewSeconds);
            }
        }
    }
}
