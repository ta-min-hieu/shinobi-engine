package dev.kage.server.database.guild;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildShopItem;
import dev.kage.guild.domain.GuildShopOutcome;
import dev.kage.guild.domain.GuildShopPort;
import dev.kage.guild.domain.GuildShopRules;
import dev.kage.guild.domain.GuildShopWallet;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;

/**
 * Hiện thực {@link GuildShopPort} (ADR-080, V77). Thứ tự khoá: túi đồ ({@link CharacterInventoryLock}) TRƯỚC dòng
 * {@code guild_member} — thứ tự chung của mọi giao dịch cấp vật phẩm (DATABASE.md §5.1). Cấp món qua
 * {@link InventoryStackMerge} (gộp chồng trước, cùng đường cửa hàng/thư/lò đan).
 */
public final class PostgresGuildShopAdapter implements GuildShopPort {

    private static final String ITEMS = """
            SELECT item_template_id, contribution_cost, required_guild_level
              FROM guild_shop_item
             ORDER BY sort_order, item_template_id
            """;

    private static final String WALLET = """
            SELECT g.level, gm.contribution, gm.contribution_spent
              FROM guild_member gm JOIN guild g ON g.id = gm.guild_id
             WHERE gm.character_id = ?
            """;

    private static final String LOCK_WALLET = WALLET + " FOR UPDATE OF gm";

    private static final String SPEND = """
            UPDATE guild_member SET contribution_spent = contribution_spent + ? WHERE character_id = ?
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private final DataSource dataSource;
    private volatile List<GuildShopItem> cachedItems;

    public PostgresGuildShopAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<GuildShopItem> items() {
        List<GuildShopItem> loaded = cachedItems;
        if (loaded == null) {
            loaded = List.copyOf(JdbcOperations.query(
                    dataSource, ITEMS, rs -> new GuildShopItem(rs.getLong(1), rs.getLong(2), rs.getInt(3))));
            cachedItems = loaded;
        }
        return loaded;
    }

    @Override
    public Optional<GuildShopWallet> walletOf(long characterId) {
        return JdbcOperations.queryOne(
                dataSource,
                WALLET,
                rs -> new GuildShopWallet(rs.getInt(1), GuildShopRules.available(rs.getLong(2), rs.getLong(3))),
                characterId);
    }

    @Override
    public GuildShopOutcome buy(long characterId, long itemTemplateId) {
        Optional<GuildShopItem> item = items().stream()
                .filter(candidate -> candidate.itemTemplateId() == itemTemplateId)
                .findFirst();
        if (item.isEmpty()) {
            return GuildShopOutcome.UNKNOWN_ITEM;
        }
        try {
            return JdbcTransactionRunner.call(dataSource, connection -> buyIn(connection, characterId, item.get()));
        } catch (Refused refused) {
            return refused.outcome;
        }
    }

    private static GuildShopOutcome buyIn(Connection connection, long characterId, GuildShopItem item) {
        CharacterInventoryLock.acquire(connection, characterId);
        Optional<GuildShopWallet> wallet = JdbcOperations.queryOne(
                connection,
                LOCK_WALLET,
                rs -> new GuildShopWallet(rs.getInt(1), GuildShopRules.available(rs.getLong(2), rs.getLong(3))),
                characterId);
        if (wallet.isEmpty()) {
            return GuildShopOutcome.NOT_IN_GUILD;
        }
        Optional<GuildShopOutcome> refusal = GuildShopRules.refusal(
                item, wallet.get().guildLevel(), wallet.get().available());
        if (refusal.isPresent()) {
            return refusal.get();
        }
        long templateId = item.itemTemplateId();
        boolean granted = InventoryStackMerge.mergeIntoExistingStack(connection, characterId, templateId, 1)
                || InventoryStackMerge.insertRespectingMaxStack(connection, characterId, templateId, 1);
        if (!granted) {
            throw new Refused(GuildShopOutcome.BAG_FULL);
        }
        JdbcOperations.update(connection, SPEND, item.contributionCost(), characterId);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "GUILD_SHOP_BUY",
                "{\"itemTemplateId\":" + templateId + ",\"contributionCost\":" + item.contributionCost() + "}",
                Timestamp.from(Instant.now()));
        return GuildShopOutcome.OK;
    }

    /** Từ chối giữa giao dịch — rollback mọi thứ đã ghi. */
    private static final class Refused extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient GuildShopOutcome outcome;

        Refused(GuildShopOutcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }
}
