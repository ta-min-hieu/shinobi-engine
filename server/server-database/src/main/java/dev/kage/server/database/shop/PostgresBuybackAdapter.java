package dev.kage.server.database.shop;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;
import dev.kage.server.database.inventory.ItemLine;
import dev.kage.shop.domain.BuybackPort;

/**
 * ADR-202 — {@code shop_buyback}. Mua lại = một transaction: khoá túi đồ (thứ tự khoá chung) → khoá dòng mua lại → cấp
 * lại món → trừ đúng giá đã nhận → xoá dòng → audit. Không đủ vàng thì ném để lùi cả phần cấp món.
 */
public final class PostgresBuybackAdapter implements BuybackPort {

    private static final String GOLD_REASON = "gold.reason.shop_buyback";

    private static final String LIST = """
            SELECT id, item_template_id, quantity, enhance_level, price, sold_at FROM shop_buyback
             WHERE character_id = ? AND sold_at >= ? ORDER BY id DESC
            """;

    private static final String LOCK_ENTRY = """
            SELECT id, item_template_id, quantity, enhance_level, price, sold_at FROM shop_buyback
             WHERE id = ? AND character_id = ? AND sold_at >= ? FOR UPDATE
            """;

    private static final String DELETE = "DELETE FROM shop_buyback WHERE id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, 'SHOP_BUYBACK', ?, now())
            """;

    /** Ném ra để lùi transaction khi không đủ vàng sau khi đã cấp món. */
    private static final class InsufficientGold extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        InsufficientGold() {
            super(null, null, false, false);
        }
    }

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresBuybackAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Entry> list(long characterId, Instant since) {
        return JdbcOperations.query(
                dataSource, LIST, PostgresBuybackAdapter::entry, characterId, Timestamp.from(since));
    }

    @Override
    public Outcome buyBack(long characterId, long entryId, Instant since) {
        try {
            return JdbcTransactionRunner.call(dataSource, connection -> buyIn(connection, characterId, entryId, since));
        } catch (InsufficientGold e) {
            return Outcome.INSUFFICIENT_GOLD;
        }
    }

    private static Outcome buyIn(Connection connection, long characterId, long entryId, Instant since) {
        CharacterInventoryLock.acquire(connection, characterId);
        Optional<Entry> found = JdbcOperations.queryOne(
                connection, LOCK_ENTRY, PostgresBuybackAdapter::entry, entryId, characterId, Timestamp.from(since));
        if (found.isEmpty()) {
            return Outcome.NOT_FOUND;
        }
        Entry entry = found.get();
        if (!grant(connection, characterId, entry)) {
            return Outcome.BAG_FULL;
        }
        if (!GoldLedger.deduct(connection, characterId, entry.price(), GOLD_REASON)) {
            throw new InsufficientGold();
        }
        JdbcOperations.update(connection, DELETE, entryId);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "{\"itemTemplateId\":%d,\"quantity\":%d,\"enhanceLevel\":%d,\"gold\":%d}"
                        .formatted(entry.itemTemplateId(), entry.quantity(), entry.enhanceLevel(), entry.price()));
        return Outcome.OK;
    }

    /**
     * Món cường hoá không gộp chồng (mở ô riêng giữ cấp — cùng luật thư ADR-083); món thường gộp trước rồi mới mở ô.
     */
    private static boolean grant(Connection connection, long characterId, Entry entry) {
        if (entry.enhanceLevel() > 0) {
            return InventoryStackMerge.insertRespectingMaxStack(
                    connection,
                    characterId,
                    new ItemLine(entry.itemTemplateId(), entry.quantity(), entry.enhanceLevel()));
        }
        return InventoryStackMerge.mergeIntoExistingStack(
                        connection, characterId, entry.itemTemplateId(), entry.quantity())
                || InventoryStackMerge.insertRespectingMaxStack(
                        connection, characterId, entry.itemTemplateId(), entry.quantity());
    }

    private static Entry entry(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Entry(
                rs.getLong(1),
                rs.getLong(2),
                rs.getInt(3),
                rs.getInt(4),
                rs.getLong(5),
                rs.getTimestamp(6).toInstant());
    }
}
