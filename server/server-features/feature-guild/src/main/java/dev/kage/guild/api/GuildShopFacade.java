package dev.kage.guild.api;

import java.util.List;
import java.util.Optional;

import dev.kage.guild.domain.GuildShopItem;
import dev.kage.guild.domain.GuildShopOutcome;
import dev.kage.guild.domain.GuildShopPort;
import dev.kage.guild.domain.GuildShopWallet;

/** API cửa hàng bang (ADR-080). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class GuildShopFacade {

    /** Composition không có cửa hàng bang (test) — không hàng, không ai ở bang nào. */
    public static final GuildShopFacade DISABLED = new GuildShopFacade(new GuildShopPort() {
        @Override
        public List<GuildShopItem> items() {
            return List.of();
        }

        @Override
        public Optional<GuildShopWallet> walletOf(long characterId) {
            return Optional.empty();
        }

        @Override
        public GuildShopOutcome buy(long characterId, long itemTemplateId) {
            return GuildShopOutcome.NOT_IN_GUILD;
        }
    });

    private final GuildShopPort port;

    /** Dựng ở composition root. */
    public GuildShopFacade(GuildShopPort port) {
        this.port = port;
    }

    /** Hàng của cửa hàng bang, theo thứ tự hiển thị. */
    public List<GuildShopItem> items() {
        return port.items();
    }

    /** Ví cống hiến; rỗng nếu không ở bang nào. */
    public Optional<GuildShopWallet> walletOf(long characterId) {
        return port.walletOf(characterId);
    }

    /** Đổi một món — {@code characterId} từ session. */
    public GuildShopOutcome buy(long characterId, long itemTemplateId) {
        return port.buy(characterId, itemTemplateId);
    }
}
