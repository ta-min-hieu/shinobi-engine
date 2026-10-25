package dev.kage.server.database.achievement;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.achievement.domain.ReputationPort;
import dev.kage.achievement.domain.ReputationRules;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;

/**
 * Hiện thực {@link ReputationPort} (ADR-109, V90). Mua: khoá túi (cùng thứ tự khoá cửa hàng bang) → khoá dòng danh
 * vọng → kiểm bậc + điểm → thêm vật phẩm (túi đầy ⇒ rollback) → trừ điểm + audit.
 */
public final class PostgresReputationAdapter implements ReputationPort {

    private static final String ADD = """
            INSERT INTO character_reputation (character_id, earned) VALUES (?, ?)
            ON CONFLICT (character_id) DO UPDATE SET earned = character_reputation.earned + EXCLUDED.earned
            """;

    private static final String STANDING = "SELECT earned, spent FROM character_reputation WHERE character_id = ?";

    private static final String LOCK_STANDING =
            "SELECT earned, spent FROM character_reputation WHERE character_id = ? FOR UPDATE";

    private static final String SHOP = """
            SELECT item_template_id, price, required_tier FROM reputation_shop_item
             ORDER BY sort_order, item_template_id
            """;

    private static final String SPEND = "UPDATE character_reputation SET spent = spent + ? WHERE character_id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresReputationAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void add(long characterId, int points) {
        JdbcOperations.update(dataSource, ADD, characterId, (long) points);
    }

    @Override
    public Standing standingOf(long characterId) {
        return JdbcOperations.queryOne(
                        dataSource, STANDING, rs -> new Standing(rs.getLong(1), rs.getLong(2)), characterId)
                .orElse(Standing.NONE);
    }

    @Override
    public List<ShopItem> shop() {
        return JdbcOperations.query(dataSource, SHOP, rs -> new ShopItem(rs.getLong(1), rs.getInt(2), rs.getInt(3)));
    }

    @Override
    public BuyOutcome buy(long characterId, long itemTemplateId) {
        Optional<ShopItem> item = shop().stream()
                .filter(c -> c.itemTemplateId() == itemTemplateId)
                .findFirst();
        if (item.isEmpty()) {
            return BuyOutcome.UNKNOWN_ITEM;
        }
        try {
            return JdbcTransactionRunner.call(dataSource, connection -> buyIn(connection, characterId, item.get()));
        } catch (Refused refused) {
            return refused.outcome;
        }
    }

    private static BuyOutcome buyIn(Connection connection, long characterId, ShopItem item) {
        CharacterInventoryLock.acquire(connection, characterId);
        Standing standing = JdbcOperations.queryOne(
                        connection, LOCK_STANDING, rs -> new Standing(rs.getLong(1), rs.getLong(2)), characterId)
                .orElse(Standing.NONE);
        if (ReputationRules.tierOf(standing.earned()) < item.requiredTier()) {
            return BuyOutcome.TIER_TOO_LOW;
        }
        if (standing.available() < item.price()) {
            return BuyOutcome.NOT_ENOUGH_REPUTATION;
        }
        long templateId = item.itemTemplateId();
        boolean granted = InventoryStackMerge.mergeIntoExistingStack(connection, characterId, templateId, 1)
                || InventoryStackMerge.insertRespectingMaxStack(connection, characterId, templateId, 1);
        if (!granted) {
            throw new Refused(BuyOutcome.BAG_FULL);
        }
        JdbcOperations.update(connection, SPEND, (long) item.price(), characterId);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "REPUTATION_SHOP_BUY",
                "{\"itemTemplateId\":" + templateId + ",\"price\":" + item.price() + "}",
                Timestamp.from(Instant.now()));
        return BuyOutcome.OK;
    }

    /** Từ chối giữa giao dịch — rollback mọi thứ đã ghi. */
    private static final class Refused extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient BuyOutcome outcome;

        Refused(BuyOutcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }
}
