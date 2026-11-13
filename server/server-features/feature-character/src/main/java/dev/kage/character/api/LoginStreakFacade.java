package dev.kage.character.api;

import java.time.LocalDate;
import java.util.Optional;

import dev.kage.character.domain.LoginStreakPort;

/**
 * API chuỗi đăng nhập liên tiếp (ADR-327): mỗi ngày (giờ Việt Nam) vào world lần đầu thì chuỗi +1, bỏ một ngày ⇒ về 1.
 * Ngày thứ {@value #FIRST_MILESTONE} và mọi bội của {@value #WEEK} thưởng linh thạch. Chạm DB — Virtual Thread.
 */
public final class LoginStreakFacade {

    /** Mốc thưởng nhỏ đầu tiên. */
    public static final int FIRST_MILESTONE = 3;

    /** Mỗi bội của số ngày này là một mốc thưởng lớn. */
    public static final int WEEK = 7;

    /** Linh thạch ở mốc {@value #FIRST_MILESTONE} ngày. */
    public static final long FIRST_REWARD = 500L;

    /** Linh thạch ở mỗi mốc bội {@value #WEEK} ngày. */
    public static final long WEEK_REWARD = 1_500L;

    /** Không lưu gì (composition test). */
    public static final LoginStreakFacade DISABLED = new LoginStreakFacade(new LoginStreakPort() {
        @Override
        public Optional<Row> advance(long characterId, LocalDate today) {
            return Optional.empty();
        }

        @Override
        public Optional<Row> find(long characterId) {
            return Optional.empty();
        }
    });

    /** Chuỗi để hiển thị: {@code current} = 0 khi đã đứt. */
    public record Status(int current, int best) {}

    private final LoginStreakPort port;

    /** Dựng ở composition root. */
    public LoginStreakFacade(LoginStreakPort port) {
        this.port = port;
    }

    /** Vào world: chuỗi mới nếu đây là lần đầu trong ngày, rỗng nếu hôm nay đã tính. */
    public Optional<LoginStreakPort.Row> advance(long characterId, LocalDate today) {
        return port.advance(characterId, today);
    }

    /** Chuỗi hiện tại tính tới {@code today}. */
    public Status status(long characterId, LocalDate today) {
        return port.find(characterId)
                .map(row -> new Status(row.lastDay().isBefore(today.minusDays(1)) ? 0 : row.streak(), row.best()))
                .orElse(new Status(0, 0));
    }

    /** Linh thạch thưởng khi chuỗi chạm {@code streak} ngày (0 nếu không phải mốc). Tĩnh để test. */
    public static long rewardFor(int streak) {
        if (streak > 0 && streak % WEEK == 0) {
            return WEEK_REWARD;
        }
        return streak == FIRST_MILESTONE ? FIRST_REWARD : 0L;
    }

    /** Mốc thưởng kế tiếp sau chuỗi {@code current} (tính cả khi chuỗi đứt ⇒ bắt đầu lại). Tĩnh để test. */
    public static int nextMilestone(int current) {
        int day = current + 1;
        while (rewardFor(day) == 0L) {
            day++;
        }
        return day;
    }
}
