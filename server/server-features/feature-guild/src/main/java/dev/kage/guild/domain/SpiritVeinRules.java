package dev.kage.guild.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

/** Luật Tranh đoạt Linh Mạch (ADR-090) — thuần, không chạm DB. */
public final class SpiritVeinRules {

    /** Nhịp chấm điểm: mỗi nhịp, bang có nhiều người đứng trong vùng nhất được 1 điểm. */
    public static final long POINT_PERIOD_SECONDS = 10L;

    /** Bang thắng giữ danh hiệu Chủ Linh Mạch chừng này ngày kể từ lúc khung đóng. */
    public static final int HOLD_DAYS = 7;

    private static final int DAYS_PER_WEEK = 7;

    private SpiritVeinRules() {}

    /** Lúc bắt đầu của khung đang mở tại {@code now}; rỗng nếu không có khung nào đang mở. */
    public static Optional<Instant> activeWindowStart(SpiritVein.Schedule schedule, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        // Hôm qua cũng xét: khung bắt đầu gần nửa đêm có thể kéo sang hôm nay.
        for (int back = 0; back <= 1; back++) {
            Optional<Instant> start = startOn(schedule, today.minusDays(back));
            if (start.isPresent() && !now.isBefore(start.get()) && now.isBefore(windowEnd(schedule, start.get()))) {
                return start;
            }
        }
        return Optional.empty();
    }

    /** Lúc bắt đầu của khung GẦN NHẤT đã đóng trước {@code now} (trong một tuần qua). */
    public static Optional<Instant> lastClosedWindowStart(SpiritVein.Schedule schedule, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        for (int back = 0; back <= DAYS_PER_WEEK; back++) {
            Optional<Instant> start = startOn(schedule, today.minusDays(back));
            if (start.isPresent() && !now.isBefore(windowEnd(schedule, start.get()))) {
                return start;
            }
        }
        return Optional.empty();
    }

    /** Lúc bắt đầu của khung SẮP TỚI sau {@code now} (trong tuần tới). */
    public static Instant nextWindowStart(SpiritVein.Schedule schedule, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        for (int ahead = 0; ahead <= DAYS_PER_WEEK; ahead++) {
            Optional<Instant> start = startOn(schedule, today.plusDays(ahead));
            if (start.isPresent() && start.get().isAfter(now)) {
                return start.get();
            }
        }
        throw new IllegalStateException("Lịch Linh Mạch không có ngày nào trong tuần: " + schedule);
    }

    /** Lúc khung bắt đầu ở {@code start} đóng lại. */
    public static Instant windowEnd(SpiritVein.Schedule schedule, Instant start) {
        return start.plus(Duration.ofMinutes(schedule.durationMinutes()));
    }

    /**
     * Bang đang chiếm vùng: bang có NHIỀU người đứng trong vùng nhất, hơn hẳn mọi bang khác. Hoà ở đầu bảng = vùng
     * đang tranh chấp, không ai được điểm.
     */
    public static Optional<Long> occupier(Map<Long, Integer> occupantsByGuild) {
        long leader = 0L;
        int best = 0;
        boolean tied = false;
        for (Map.Entry<Long, Integer> entry : occupantsByGuild.entrySet()) {
            int count = entry.getValue();
            if (count > best) {
                leader = entry.getKey();
                best = count;
                tied = false;
            } else if (count == best) {
                tied = true;
            }
        }
        return best > 0 && !tied ? Optional.of(leader) : Optional.empty();
    }

    private static Optional<Instant> startOn(SpiritVein.Schedule schedule, LocalDate day) {
        if (day.getDayOfWeek().getValue() != schedule.weekday()) {
            return Optional.empty();
        }
        return Optional.of(day.atStartOfDay(ZoneOffset.UTC)
                .plusMinutes(schedule.startMinute())
                .toInstant());
    }
}
