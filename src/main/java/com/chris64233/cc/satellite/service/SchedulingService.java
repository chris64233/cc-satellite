package com.chris64233.cc.satellite.service;

import com.chris64233.cc.satellite.domain.Antenna;
import com.chris64233.cc.satellite.domain.ContactStatus;
import com.chris64233.cc.satellite.domain.ContactTask;
import com.chris64233.cc.satellite.domain.GroundStation;
import com.chris64233.cc.satellite.domain.PassWindow;
import com.chris64233.cc.satellite.repo.ContactTaskRepository;
import com.chris64233.cc.satellite.repo.GroundStationRepository;
import com.chris64233.cc.satellite.repo.PassWindowRepository;
import com.chris64233.cc.satellite.web.dto.ScheduleContactRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 联系任务排程服务。
 *
 * <p>核心规则：
 * <ul>
 *   <li>实际联系必须完整落在过境窗口内，且频段与窗口、地面站兼容；</li>
 *   <li>同一天线上的任务不能时间重叠；相邻任务属于不同卫星时，
 *       间隔必须不小于天线的转向准备时长，同一卫星可以首尾相接；</li>
 *   <li>在全部可行位置中，按“距离期望开始时间最近、开始时间更早、天线编号更小”
 *       的顺序稳定选择；</li>
 *   <li>排程在事务内完成并对地面站加悲观写锁，失败时不留下任何占用，
 *       并发下也不会产生重叠或违反转向间隔。</li>
 * </ul>
 */
@Service
public class SchedulingService {

    private final GroundStationRepository stationRepository;
    private final PassWindowRepository windowRepository;
    private final ContactTaskRepository taskRepository;
    private final Clock clock;

    public SchedulingService(GroundStationRepository stationRepository,
                             PassWindowRepository windowRepository,
                             ContactTaskRepository taskRepository,
                             Clock clock) {
        this.stationRepository = stationRepository;
        this.windowRepository = windowRepository;
        this.taskRepository = taskRepository;
        this.clock = clock;
    }

    /** 排程结果：任务以及是否本次新建（幂等重放时为 false）。 */
    public record ScheduleResult(ContactTask task, boolean created) {
    }

    @Transactional
    public ScheduleResult schedule(ScheduleContactRequest request) {
        PassWindow window = windowRepository.findById(request.windowId())
                .orElseThrow(() -> new NotFoundException("过境窗口不存在: " + request.windowId()));
        // 对地面站加悲观写锁，串行化同一站点的排程决策
        GroundStation station = stationRepository.findByIdForUpdate(window.getStation().getId())
                .orElseThrow(() -> new NotFoundException("地面站不存在: " + window.getStation().getId()));

        // 幂等：相同键 + 相同内容返回原排程；相同键 + 不同内容报冲突
        var existing = taskRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            ContactTask task = existing.get();
            if (task.matchesRequest(request.windowId(), request.band(),
                    request.durationMinutes(), request.desiredStart())) {
                return new ScheduleResult(task, false);
            }
            throw new ConflictException("幂等键已被不同内容的请求使用: " + request.idempotencyKey());
        }

        if (!window.getBands().contains(request.band())) {
            throw new UnschedulableException("过境窗口不支持频段: " + request.band());
        }
        if (!station.getBands().contains(request.band())) {
            throw new UnschedulableException("地面站不支持频段: " + request.band());
        }

        Duration duration = Duration.ofMinutes(request.durationMinutes());
        Placement best = findBestPlacement(station, window, duration, request.desiredStart());
        if (best == null) {
            // 事务回滚，不留下任何占用
            throw new UnschedulableException("过境窗口内不存在满足约束的可行位置");
        }

        ContactTask task = new ContactTask(
                request.idempotencyKey(), window, station, best.antenna(),
                request.band(), request.durationMinutes(), request.desiredStart(),
                best.start(), best.end(), clock.instant());
        return new ScheduleResult(taskRepository.save(task), true);
    }

    /**
     * 取消未开始的任务：释放天线占用。重复取消是幂等空操作，只生效一次。
     */
    @Transactional
    public ContactTask cancel(long taskId) {
        ContactTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new NotFoundException("联系任务不存在: " + taskId));
        if (task.getStatus() == ContactStatus.CANCELLED) {
            return task;
        }
        Instant now = clock.instant();
        if (!task.getStartTime().isAfter(now)) {
            throw new ConflictException("任务已开始，无法取消: " + taskId);
        }
        task.cancel(now);
        return taskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public ContactTask contactDetails(long taskId) {
        return taskRepository.findDetailedById(taskId)
                .orElseThrow(() -> new NotFoundException("联系任务不存在: " + taskId));
    }

    @Transactional(readOnly = true)
    public List<ContactTask> stationSchedule(String stationCode, Instant from, Instant to) {
        GroundStation station = stationRepository.findByCode(stationCode)
                .orElseThrow(() -> new NotFoundException("地面站不存在: " + stationCode));
        return taskRepository.findStationSchedule(station.getId(), ContactStatus.SCHEDULED, from, to);
    }

    /**
     * 在站点所有天线上寻找最优可行位置。
     * 比较顺序：距期望开始时间最近 → 开始时间更早 → 天线编号更小。
     */
    private Placement findBestPlacement(GroundStation station, PassWindow window,
                                        Duration duration, Instant desiredStart) {
        Placement best = null;
        for (Antenna antenna : station.getAntennas()) {
            Placement candidate = bestOnAntenna(antenna, window, duration, desiredStart);
            if (candidate != null && (best == null || candidate.compareTo(best) < 0)) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * 在单根天线上计算距期望时间最近的可行位置。
     *
     * <p>把窗口内已被占用的时间段（含不同卫星所需的转向间隔）扣除后，
     * 得到若干可行的开始时间区间，在每个区间内取距期望时间最近的点。
     */
    private Placement bestOnAntenna(Antenna antenna, PassWindow window,
                                    Duration duration, Instant desiredStart) {
        Instant windowStart = window.getStartTime();
        Instant windowEnd = window.getEndTime();
        String satellite = window.getSatellite();
        long slewSeconds = antenna.getSlewSeconds();

        List<ContactTask> occupied = taskRepository.findByAntennaAndTimeRange(
                antenna.getId(), ContactStatus.SCHEDULED, windowStart, windowEnd);

        Placement best = null;
        // cursor：当前可用的最早开始时间（已扣除前一个任务所需的转向间隔）
        Instant cursor = windowStart;
        for (ContactTask task : occupied) {
            boolean sameSatellite = task.getSatellite().equals(satellite);
            // 新任务必须在该任务开始前结束；不同卫星时还需预留转向间隔
            Instant latestStart = task.getStartTime()
                    .minusSeconds(sameSatellite ? 0 : slewSeconds)
                    .minus(duration);
            best = min(best, consider(antenna, cursor, latestStart, duration, desiredStart));
            // 下一个可行开始时间：该任务结束后，不同卫星时再加转向间隔
            Instant next = task.getEndTime().plusSeconds(sameSatellite ? 0 : slewSeconds);
            if (next.isAfter(cursor)) {
                cursor = next;
            }
        }
        best = min(best, consider(antenna, cursor, windowEnd.minus(duration), duration, desiredStart));
        return best;
    }

    /** 在开始时间可行区间 [earliest, latest] 内取距期望时间最近的候选位置。 */
    private Placement consider(Antenna antenna, Instant earliest, Instant latest,
                               Duration duration, Instant desiredStart) {
        if (earliest.isAfter(latest)) {
            return null;
        }
        Instant start;
        if (desiredStart.isBefore(earliest)) {
            start = earliest;
        } else if (desiredStart.isAfter(latest)) {
            start = latest;
        } else {
            start = desiredStart;
        }
        return new Placement(antenna, start, start.plus(duration), Duration.between(desiredStart, start).abs());
    }

    private static Placement min(Placement a, Placement b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return b.compareTo(a) < 0 ? b : a;
    }

    /** 一个可行排程位置及其与期望时间的距离。 */
    private record Placement(Antenna antenna, Instant start, Instant end, Duration distance)
            implements Comparable<Placement> {

        @Override
        public int compareTo(Placement other) {
            int c = distance.compareTo(other.distance);
            if (c != 0) {
                return c;
            }
            c = start.compareTo(other.start);
            if (c != 0) {
                return c;
            }
            return Integer.compare(antenna.getAntennaNumber(), other.antenna.getAntennaNumber());
        }
    }
}
