package dev.kage.server.database.trade;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcOperations;
import dev.kage.trade.domain.OwnedItemSnapshot;
import dev.kage.trade.domain.TradeInventoryPort;
import dev.kage.trade.domain.Transaction;

/**
 * Hiện thực {@link TradeInventoryPort} trên PostgreSQL — SQL riêng cho {@code inventory_item},
 * bản sao ý tưởng {@code PostgresShopInventoryGatewayAdapter}. {@link #transferItem} là bước XÁC
 * THỰC THẬT DUY NHẤT của toàn bộ feature-trade (xem feature-trade/CLAUDE.md) — khoá dòng FOR
 * UPDATE, kiểm còn đúng chủ + đủ số lượng, tách stack nếu cần.
 */
public final class PostgresTradeInventoryAdapter implements TradeInventoryPort {

    // PLACEHOLDER trùng PostgresInventoryAdapter.DEFAULT_CAPACITY (GDD chưa chốt sức chứa thật).
    private static final int DEFAULT_CAPACITY = 20;

    private static final String FIND_BY_ID = """
            SELECT id, character_id, item_template_id, quantity, enhance_level FROM inventory_item WHERE id = ?
            """;

    private static final String SELECT_FOR_UPDATE = """
            SELECT character_id, item_template_id, quantity, enhance_level FROM inventory_item WHERE id = ? FOR UPDATE
            """;

    private static final String COUNT_ITEMS = """
            SELECT COUNT(*) FROM inventory_item WHERE character_id = ?
            """;

    private static final String UPDATE_OWNER = """
            UPDATE inventory_item SET character_id = ? WHERE id = ?
            """;

    private static final String DECREMENT_QUANTITY = """
            UPDATE inventory_item SET quantity = quantity - ? WHERE id = ?
            """;

    private static final String INSERT_ROW = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresTradeInventoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<OwnedItemSnapshot> findItemById(long itemId) {
        return JdbcOperations.queryOne(dataSource, FIND_BY_ID, PostgresTradeInventoryAdapter::mapRow, itemId);
    }

    @Override
    public TransferOutcome transferItem(Transaction tx, TransferRequest request) {
        Connection connection = ((JdbcTransaction) tx).connection();
        Optional<OwnedItemSnapshot> current = selectForUpdate(connection, request.itemId());
        if (current.isEmpty() || current.get().characterId() != request.fromCharacterId()) {
            return TransferOutcome.ITEM_NOT_OWNED;
        }
        if (current.get().quantity() < request.quantity()) {
            return TransferOutcome.INSUFFICIENT_QUANTITY;
        }
        boolean isWholeRow = current.get().quantity() == request.quantity();
        if (!isWholeRow && countItems(connection, request.toCharacterId()) >= DEFAULT_CAPACITY) {
            return TransferOutcome.RECIPIENT_INVENTORY_FULL;
        }
        if (isWholeRow) {
            JdbcOperations.update(connection, UPDATE_OWNER, request.toCharacterId(), request.itemId());
        } else {
            JdbcOperations.update(connection, DECREMENT_QUANTITY, request.quantity(), request.itemId());
            insertRow(connection, request, current.get());
        }
        return TransferOutcome.OK;
    }

    @Override
    public boolean exceedsCapacity(Transaction tx, long characterId) {
        return countItems(((JdbcTransaction) tx).connection(), characterId) > DEFAULT_CAPACITY;
    }

    private static Optional<OwnedItemSnapshot> selectForUpdate(Connection connection, long itemId) {
        return JdbcOperations.queryOne(
                connection,
                SELECT_FOR_UPDATE,
                rs -> new OwnedItemSnapshot(
                        itemId,
                        rs.getLong("character_id"),
                        rs.getLong("item_template_id"),
                        rs.getInt("quantity"),
                        rs.getInt("enhance_level")),
                itemId);
    }

    private static int countItems(Connection connection, long characterId) {
        return JdbcOperations.queryOne(connection, COUNT_ITEMS, rs -> rs.getInt(1), characterId)
                .orElse(0);
    }

    private static void insertRow(Connection connection, TransferRequest request, OwnedItemSnapshot original) {
        // slot_index — nối vào cuối danh sách hiện có.
        JdbcOperations.update(
                connection,
                INSERT_ROW,
                request.toCharacterId(),
                original.itemTemplateId(),
                request.quantity(),
                original.enhanceLevel(),
                countItems(connection, request.toCharacterId()));
    }

    private static OwnedItemSnapshot mapRow(ResultSet rs) throws SQLException {
        return new OwnedItemSnapshot(
                rs.getLong("id"),
                rs.getLong("character_id"),
                rs.getLong("item_template_id"),
                rs.getInt("quantity"),
                rs.getInt("enhance_level"));
    }
}
