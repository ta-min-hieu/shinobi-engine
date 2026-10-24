package dev.kage.guild.domain;

import java.util.Optional;

/**
 * Luật đổi hàng bang (ADR-080 D1/D3) — hàm thuần; adapter gọi nó DƯỚI khoá dòng thành viên để số dư không đổi giữa
 * lúc hỏi và lúc ghi (cùng khuôn {@code donate} của {@link GuildProgressionPort}: mỗi hàm GHI là một transaction trọn).
 */
public final class GuildShopRules {

    private GuildShopRules() {}

    /** Điểm cống hiến còn tiêu được: tổng đã góp trừ đã tiêu (không âm kể cả dữ liệu lệch). */
    public static long available(long contribution, long contributionSpent) {
        return Math.max(0L, contribution - contributionSpent);
    }

    /** Lý do từ chối đầu tiên, hoặc rỗng nếu đổi được. */
    public static Optional<GuildShopOutcome> refusal(GuildShopItem item, int guildLevel, long available) {
        if (guildLevel < item.requiredGuildLevel()) {
            return Optional.of(GuildShopOutcome.GUILD_LEVEL_TOO_LOW);
        }
        if (available < item.contributionCost()) {
            return Optional.of(GuildShopOutcome.NOT_ENOUGH_CONTRIBUTION);
        }
        return Optional.empty();
    }
}
