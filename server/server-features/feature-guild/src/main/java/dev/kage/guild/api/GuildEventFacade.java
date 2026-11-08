package dev.kage.guild.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.kage.guild.domain.GuildEventPort;

/**
 * API lịch hoạt động bang (ADR-256): tiêu đề ≤ {@value #MAX_TITLE} ký tự, hẹn trong 1 phút – 7 ngày tới, mỗi bang tối
 * đa {@value #MAX_UPCOMING} buổi sắp tới; nhắc trước {@link #REMIND_AHEAD}. Chạm DB — chỉ gọi trên Virtual Thread.
 */
public final class GuildEventFacade {

    public static final int MAX_TITLE = 60;
    public static final int MAX_UPCOMING = 5;

    /** Nhắc trước bao lâu. */
    public static final Duration REMIND_AHEAD = Duration.ofMinutes(10);

    private static final Duration MIN_LEAD = Duration.ofMinutes(1);
    private static final Duration MAX_LEAD = Duration.ofDays(7);

    /** Không có bang nào (composition test). */
    public static final GuildEventFacade DISABLED = new GuildEventFacade(new GuildEventPort() {
        @Override
        public CreateOutcome create(Draft draft, int maxUpcoming) {
            return CreateOutcome.NOT_IN_GUILD;
        }

        @Override
        public CancelOutcome cancel(long characterId, long eventId) {
            return CancelOutcome.NOT_FOUND;
        }

        @Override
        public List<Event> upcoming(long characterId, Instant now) {
            return List.of();
        }

        @Override
        public List<Due> due(Instant now, Instant until) {
            return List.of();
        }

        @Override
        public void markReminded(long eventId) {
            // không lưu
        }
    });

    private final GuildEventPort port;

    /** Dựng ở composition root. */
    public GuildEventFacade(GuildEventPort port) {
        this.port = port;
    }

    /** Tiêu đề và giờ hẹn hợp lệ lúc {@code now}. Tĩnh để test. */
    public static boolean isValid(String title, Instant startsAt, Instant now) {
        return !title.isBlank()
                && title.strip().length() <= MAX_TITLE
                && !startsAt.isBefore(now.plus(MIN_LEAD))
                && !startsAt.isAfter(now.plus(MAX_LEAD));
    }

    /** Bang chủ / phó hẹn một buổi. */
    public GuildEventPort.CreateOutcome create(long characterId, String title, Instant startsAt, Instant now) {
        if (!isValid(title, startsAt, now)) {
            return GuildEventPort.CreateOutcome.INVALID;
        }
        return port.create(new GuildEventPort.Draft(characterId, title.strip(), startsAt, now), MAX_UPCOMING);
    }

    /** Huỷ một buổi. */
    public GuildEventPort.CancelOutcome cancel(long characterId, long eventId) {
        return port.cancel(characterId, eventId);
    }

    /** Các buổi sắp tới của bang người này. */
    public List<GuildEventPort.Event> upcoming(long characterId, Instant now) {
        return port.upcoming(characterId, now);
    }

    /** Buổi cần nhắc lúc {@code now} (bắt đầu trong {@link #REMIND_AHEAD} tới, chưa nhắc). */
    public List<GuildEventPort.Due> due(Instant now) {
        return port.due(now, now.plus(REMIND_AHEAD));
    }

    /** Đánh dấu đã nhắc. */
    public void markReminded(long eventId) {
        port.markReminded(eventId);
    }
}
