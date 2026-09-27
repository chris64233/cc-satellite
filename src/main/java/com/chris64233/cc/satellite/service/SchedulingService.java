package com.chris64233.cc.satellite.service;

import com.chris64233.cc.satellite.domain.Antenna;
import com.chris64233.cc.satellite.domain.Contact;
import com.chris64233.cc.satellite.domain.ContactStatus;
import com.chris64233.cc.satellite.domain.GroundStation;
import com.chris64233.cc.satellite.domain.VisibilityWindow;
import com.chris64233.cc.satellite.repository.ContactRepository;
import com.chris64233.cc.satellite.repository.GroundStationRepository;
import com.chris64233.cc.satellite.repository.VisibilityWindowRepository;
import com.chris64233.cc.satellite.service.error.ConflictException;
import com.chris64233.cc.satellite.service.error.NotFoundException;
import com.chris64233.cc.satellite.service.error.UnschedulableException;
import com.chris64233.cc.satellite.web.dto.ContactResponse;
import com.chris64233.cc.satellite.web.dto.ScheduleContactRequest;
import com.chris64233.cc.satellite.web.dto.StationScheduleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 联系任务排程：在可见过境窗口内为请求分配天线与起止时间。
 *
 * <p>核心规则：
 * <ul>
 *   <li>联系必须完整落在窗口内，且请求频段同时被窗口与地面站支持；</li>
 *   <li>同一天线上的任务时间不得重叠；相邻任务属于不同卫星时，间隔不得小于天线转向时长，
 *       属于同一卫星时可以首尾相接；</li>
 *   <li>可行位置中按 |开始-期望| 最小、开始更早、天线编号更小 的顺序稳定选择；</li>
 *   <li>同一站点的排程通过地面站行的悲观写锁串行化，保证并发下不产生重叠。</li>
 * </ul>
 */
@Service
public class SchedulingService {

    private final GroundStationRepository stationRepository;
    private final VisibilityWindowRepository windowRepository;
    private final ContactRepository contactRepository;
    private final Clock clock;

    public SchedulingService(GroundStationRepository stationRepository,
                             VisibilityWindowRepository windowRepository,
                             ContactRepository contactRepository,
                             Clock clock) {
        this.stationRepository = stationRepository;
        this.windowRepository = windowRepository;
        this.contactRepository = contactRepository;
        this.clock = clock;
    }

    @Transactional
    public ContactResponse schedule(ScheduleContactRequest request) {
        // 幂等：同键同内容直接返回原排程；同键不同内容返回冲突。
        Optional<Contact> replay = contactRepository.findByIdempotencyKey(request.idempotencyKey());
        if (replay.isPresent()) {
            return resolveReplay(replay.get(), request);
        }

        VisibilityWindow window = windowRepository.findById(request.windowId())
                .orElseThrow(() -> new NotFoundException("过境窗口不存在: " + request.windowId()));

        // 对地面站加悲观写锁，串行化本站点上的所有排程。
        GroundStation station = stationRepository.findByCodeForUpdate(window.getStation().getCode())
                .orElseThrow(() -> new NotFoundException("地面站不存在: " + window.getStation().getCode()));

        // 加锁后再次检查幂等键，防止并发下同键请求双双通过首次检查。
        replay = contactRepository.findByIdempotencyKey(request.idempotencyKey());
        if (replay.isPresent()) {
            return resolveReplay(replay.get(), request);
        }

        if (!window.getBands().contains(request.band())) {
            throw new UnschedulableException("窗口不支持频段: " + request.band());
        }
        if (!station.getSupportedBands().contains(request.band())) {
            throw new UnschedulableException("地面站不支持频段: " + request.band());
        }

        Duration duration = Duration.ofMinutes(request.durationMinutes());
        if (window.getStartTime().plus(duration).isAfter(window.getEndTime())) {
            throw new UnschedulableException("窗口时长不足，无法容纳 " + request.durationMinutes() + " 分钟的联系");
        }

        Placement placement = findPlacement(station, window, request, duration)
                .orElseThrow(() -> new UnschedulableException("所有天线均无满足转向约束的可行位置"));

        Contact contact = new Contact(
                request.idempotencyKey(),
                request.contentHash(),
                window,
                placement.antenna(),
                request.band(),
                request.durationMinutes(),
                request.desiredStart(),
                placement.start(),
                placement.start().plus(duration),
                clock.instant());
        return ContactResponse.from(contactRepository.save(contact));
    }

    private ContactResponse resolveReplay(Contact existing, ScheduleContactRequest request) {
        if (!existing.getRequestHash().equals(request.contentHash())) {
            throw new ConflictException("幂等键已用于不同内容的请求: " + request.idempotencyKey());
        }
        return ContactResponse.from(existing);
    }

    /**
     * 在每根天线的既有任务间隙中寻找可行位置。候选开始时间取期望时间在可行区间内的最近点，
     * 再按 (|开始-期望|, 开始时间, 天线编号) 稳定选择最优。
     */
    private Optional<Placement> findPlacement(GroundStation station, VisibilityWindow window,
                                              ScheduleContactRequest request, Duration duration) {
        String satellite = window.getSatellite();
        Instant windowStart = window.getStartTime();
        Instant windowEnd = window.getEndTime();

        List<Placement> candidates = new ArrayList<>();
        for (Antenna antenna : station.getAntennas()) {
            Duration slew = Duration.ofSeconds(antenna.getSlewSeconds());
            // 向两侧放宽一个转向时长，覆盖窗口边缘之外任务的转向约束。
            List<Contact> neighbours = contactRepository.findScheduledOverlapping(
                    antenna.getId(), windowStart.minus(slew), windowEnd.plus(slew));

            Contact previous = null;
            for (int i = 0; i <= neighbours.size(); i++) {
                Contact next = i < neighbours.size() ? neighbours.get(i) : null;

                Instant earliest = windowStart;
                if (previous != null) {
                    Instant bound = previous.getEndTime();
                    if (!previous.getSatellite().equals(satellite)) {
                        bound = bound.plus(slew);
                    }
                    if (bound.isAfter(earliest)) {
                        earliest = bound;
                    }
                }
                Instant latest = windowEnd;
                if (next != null) {
                    Instant bound = next.getStartTime();
                    if (!next.getSatellite().equals(satellite)) {
                        bound = bound.minus(slew);
                    }
                    if (bound.isBefore(latest)) {
                        latest = bound;
                    }
                }

                Instant latestStart = latest.minus(duration);
                if (!latestStart.isBefore(earliest)) {
                    Instant start = request.desiredStart();
                    if (start.isBefore(earliest)) {
                        start = earliest;
                    } else if (start.isAfter(latestStart)) {
                        start = latestStart;
                    }
                    candidates.add(new Placement(antenna, start));
                }
                previous = next;
            }
        }

        return candidates.stream().min(Comparator
                .comparing((Placement p) -> Duration.between(request.desiredStart(), p.start()).abs())
                .thenComparing(Placement::start)
                .thenComparing(p -> p.antenna().getId()));
    }

    @Transactional
    public ContactResponse cancel(long contactId) {
        Contact contact = contactRepository.findByIdForUpdate(contactId)
                .orElseThrow(() -> new NotFoundException("联系任务不存在: " + contactId));
        if (contact.getStatus() == ContactStatus.CANCELLED) {
            // 取消只生效一次：重复取消直接返回当前状态。
            return ContactResponse.from(contact);
        }
        if (!clock.instant().isBefore(contact.getStartTime())) {
            throw new ConflictException("任务已开始，无法取消: " + contactId);
        }
        contact.cancel(clock.instant());
        return ContactResponse.from(contactRepository.save(contact));
    }

    @Transactional(readOnly = true)
    public ContactResponse getContact(long contactId) {
        return contactRepository.findById(contactId)
                .map(ContactResponse::from)
                .orElseThrow(() -> new NotFoundException("联系任务不存在: " + contactId));
    }

    @Transactional(readOnly = true)
    public StationScheduleResponse getStationSchedule(String stationCode) {
        GroundStation station = stationRepository.findById(stationCode)
                .orElseThrow(() -> new NotFoundException("地面站不存在: " + stationCode));

        Map<Long, StationScheduleResponse.AntennaSchedule> byAntenna = new LinkedHashMap<>();
        Map<Long, List<ContactResponse>> contactsByAntenna = new LinkedHashMap<>();
        for (Antenna antenna : station.getAntennas()) {
            contactsByAntenna.put(antenna.getId(), new ArrayList<>());
        }
        for (Contact contact : contactRepository.findScheduledByStation(stationCode)) {
            contactsByAntenna.get(contact.getAntenna().getId()).add(ContactResponse.from(contact));
        }
        for (Antenna antenna : station.getAntennas()) {
            byAntenna.put(antenna.getId(), new StationScheduleResponse.AntennaSchedule(
                    antenna.getId(), antenna.getCode(), contactsByAntenna.get(antenna.getId())));
        }
        return new StationScheduleResponse(station.getCode(), List.copyOf(byAntenna.values()));
    }

    private record Placement(Antenna antenna, Instant start) {
    }
}
