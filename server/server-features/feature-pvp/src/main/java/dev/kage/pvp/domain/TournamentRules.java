package dev.kage.pvp.domain;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;

/**
 * Luật luận võ đại hội (ADR-125) — hàm thuần. Mỗi tuần một giải vào {@link Schedule#day()} lúc {@link Schedule#time()}
 * (giờ Việt Nam); đăng ký mở từ 0h ngày đó tới giờ bắt đầu. Loại trực tiếp: mỗi vòng ghép cặp ngẫu nhiên, lẻ người thì
 * một người được đi thẳng; trận quá {@link #MATCH_TIMEOUT} thì xử thua người thách (bên A).
 */
public final class TournamentRules {

    public static final int MIN_LEVEL = 10;
    public static final int MAX_ENTRANTS = 64;
    public static final Duration MATCH_TIMEOUT = Duration.ofMinutes(5);
    public static final long CHAMPION_GOLD = 5_000L;
    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Lịch mặc định: Chủ nhật 20:00 giờ VN. */
    public static final Schedule DEFAULT_SCHEDULE = new Schedule(DayOfWeek.SUNDAY, LocalTime.of(20, 0));

    /** Ngày trong tuần + giờ bắt đầu giải. */
    public record Schedule(DayOfWeek day, LocalTime time) {

        /** Đọc {@code "SUNDAY 20:00"} (biến môi trường {@code KAGE_TOURNAMENT_AT}); rỗng/hỏng ⇒ mặc định. */
        public static Schedule parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return DEFAULT_SCHEDULE;
            }
            String[] parts = raw.strip().split("\\s+", 2);
            try {
                return parts.length == 2
                        ? new Schedule(DayOfWeek.valueOf(parts[0].toUpperCase(Locale.ROOT)), LocalTime.parse(parts[1]))
                        : DEFAULT_SCHEDULE;
            } catch (IllegalArgumentException | DateTimeParseException e) {
                return DEFAULT_SCHEDULE;
            }
        }
    }

    private TournamentRules() {}

    /** Giờ bắt đầu của giải trong tuần chứa {@code now} (tuần tính tới hết ngày diễn ra giải). */
    public static Instant startOfWeek(Instant now, Schedule schedule) {
        ZonedDateTime local = now.atZone(VIETNAM);
        LocalDate day = local.toLocalDate().with(TemporalAdjusters.nextOrSame(schedule.day()));
        return day.atTime(schedule.time()).atZone(VIETNAM).toInstant();
    }

    /** Đang trong giờ đăng ký: đúng ngày diễn ra giải và chưa tới giờ bắt đầu. */
    public static boolean registrationOpen(Instant now, Schedule schedule) {
        ZonedDateTime local = now.atZone(VIETNAM);
        return local.getDayOfWeek() == schedule.day() && local.toLocalTime().isBefore(schedule.time());
    }

    /** Giải hôm nay (đúng ngày diễn ra) — rỗng nếu hôm nay không phải ngày giải. */
    public static Optional<Instant> todaysStart(Instant now, Schedule schedule) {
        ZonedDateTime local = now.atZone(VIETNAM);
        return local.getDayOfWeek() == schedule.day()
                ? Optional.of(local.toLocalDate()
                        .atTime(schedule.time())
                        .atZone(VIETNAM)
                        .toInstant())
                : Optional.empty();
    }
}
