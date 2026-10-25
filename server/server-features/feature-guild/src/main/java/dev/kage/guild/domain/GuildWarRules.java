package dev.kage.guild.domain;

import java.time.Duration;
import java.util.Optional;

/** Luật bang chiến (ADR-102) — thuần, không chạm DB. Số là PLACEHOLDER cân bằng. */
public final class GuildWarRules {

    /** Vàng bang chủ trả (đốt) khi tuyên chiến. */
    public static final long DECLARE_COST = 10_000L;

    /** Tuyên xong bao lâu thì bắt đầu đánh — đủ để hai bang tập hợp. */
    public static final Duration PREPARATION = Duration.ofMinutes(5);

    /** Thời lượng trận. */
    public static final Duration DURATION = Duration.ofMinutes(60);

    /** Cặp bang vừa đánh xong phải chờ chừng này mới tuyên lại nhau. */
    public static final Duration REMATCH_COOLDOWN = Duration.ofHours(24);

    /** Vàng mỗi thành viên bang thắng nhận qua thư. */
    public static final long WINNER_REWARD_GOLD = 2_000L;

    /** Nhịp luồng nền: dựng lại bảng "ai đang chiến với ai", báo trận mở, chốt trận hết giờ. */
    public static final long ROSTER_PERIOD_SECONDS = 10L;

    private GuildWarRules() {}

    /** Bang thắng của một trận theo số lần hạ; hoà ⇒ rỗng. */
    public static Optional<GuildWar.Side> winner(GuildWar war) {
        int attackerKills = war.attacker().kills();
        int defenderKills = war.defender().kills();
        if (attackerKills == defenderKills) {
            return Optional.empty();
        }
        return Optional.of(attackerKills > defenderKills ? war.attacker() : war.defender());
    }
}
