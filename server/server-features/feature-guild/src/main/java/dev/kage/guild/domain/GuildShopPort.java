package dev.kage.guild.domain;

import java.util.List;
import java.util.Optional;

/**
 * Cửa hàng bang (ADR-080) — hiện thực ở server-database. Hàm GHI là một transaction trọn trong adapter (cùng khuôn
 * {@link GuildProgressionPort#donate}): khoá túi đồ → khoá dòng thành viên → {@link GuildShopRules} → cấp món →
 * cộng {@code contribution_spent} → audit.
 */
public interface GuildShopPort {

    /** Hàng của cửa hàng bang, theo thứ tự hiển thị (tham số — đọc một lần). */
    List<GuildShopItem> items();

    /** Ví cống hiến của {@code characterId}; rỗng nếu không ở bang nào. */
    Optional<GuildShopWallet> walletOf(long characterId);

    /** Đổi một món — nguyên tử. */
    GuildShopOutcome buy(long characterId, long itemTemplateId);
}
