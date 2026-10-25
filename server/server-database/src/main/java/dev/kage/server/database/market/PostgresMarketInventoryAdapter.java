package dev.kage.server.database.market;

import java.sql.Connection;
import java.util.Optional;

import dev.kage.market.domain.MarketInventoryPort;
import dev.kage.market.domain.MarketOutcome;
import dev.kage.market.domain.Transaction;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;
import dev.kage.server.database.inventory.TradeOfferedQuantity;

/**
 * Túi đồ nhìn từ phường thị (ADR-063 D2/D3) — SQL riêng trên {@code inventory_item}, cùng cách feature-trade
 * và thư (adapter ở tầng hạ tầng được đụng bảng của feature khác; application/domain thì không).
 */
public final class PostgresMarketInventoryAdapter implements MarketInventoryPort {

    /** Dòng của CHÍNH người bán, kèm phần đang chào giao dịch và cờ đang mặc — khoá dòng. */
    private static final String SELECT_FOR_UPDATE = "SELECT ii.item_template_id, ii.quantity, ii.enhance_level, "
            + TradeOfferedQuantity.OF_II
            + " AS offered, EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = ii.id) AS equipped,"
            + " (EXISTS (SELECT 1 FROM item_socket s WHERE s.inventory_item_id = ii.id)"
            + " OR EXISTS (SELECT 1 FROM pet_state p WHERE p.inventory_item_id = ii.id)"
            + " OR EXISTS (SELECT 1 FROM item_refinement r WHERE r.inventory_item_id = ii.id)) AS socketed"
            + " FROM inventory_item ii WHERE ii.id = ? AND ii.character_id = ? FOR UPDATE OF ii";

    private static final String DECREMENT = "UPDATE inventory_item SET quantity = quantity - ? WHERE id = ?";
    private static final String DELETE = "DELETE FROM inventory_item WHERE id = ?";

    /** Ảnh chụp dòng túi vừa khoá. */
    private record Row(long itemTemplateId, int quantity, int enhanceLevel, int offered, boolean equipped) {}

    /** Đã khảm Linh Ngọc thì coi như đã cường hoá: thư trả món hết hạn không chở được ngọc (ADR-064 D5). */
    private static int effectiveEnhance(int enhanceLevel, boolean socketed) {
        return socketed ? Math.max(1, enhanceLevel) : enhanceLevel;
    }

    @Override
    public void lock(Transaction tx, long characterId) {
        CharacterInventoryLock.acquire(connection(tx), characterId);
    }

    @Override
    public TakeResult take(Transaction tx, long characterId, long inventoryItemId, int quantity) {
        Connection connection = connection(tx);
        Optional<Row> found = JdbcOperations.queryOne(
                connection,
                SELECT_FOR_UPDATE,
                rs -> new Row(
                        rs.getLong("item_template_id"),
                        rs.getInt("quantity"),
                        effectiveEnhance(rs.getInt("enhance_level"), rs.getBoolean("socketed")),
                        rs.getInt("offered"),
                        rs.getBoolean("equipped")),
                inventoryItemId,
                characterId);
        Optional<MarketOutcome> refused =
                found.isEmpty() ? Optional.of(MarketOutcome.NOT_FOUND) : refusal(found.get(), quantity);
        if (refused.isPresent()) {
            return new TakeResult.Refused(refused.get());
        }
        Row row = found.get();
        if (row.quantity() == quantity) {
            JdbcOperations.update(connection, DELETE, inventoryItemId);
        } else {
            JdbcOperations.update(connection, DECREMENT, quantity, inventoryItemId);
        }
        return new TakeResult.Taken(row.itemTemplateId());
    }

    /** Lý do không treo được dòng đã khoá (D2), rỗng nếu được. */
    private static Optional<MarketOutcome> refusal(Row row, int quantity) {
        if (quantity > row.quantity()) {
            return Optional.of(MarketOutcome.INVALID_QUANTITY);
        }
        if (row.equipped()) {
            return Optional.of(MarketOutcome.EQUIPPED);
        }
        if (row.enhanceLevel() > 0) {
            return Optional.of(MarketOutcome.ENHANCED_NOT_ALLOWED);
        }
        if (row.quantity() - row.offered() < quantity) {
            return Optional.of(MarketOutcome.ITEM_IN_TRADE);
        }
        return Optional.empty();
    }

    @Override
    public boolean give(Transaction tx, long characterId, long itemTemplateId, int quantity) {
        Connection connection = connection(tx);
        return InventoryStackMerge.mergeIntoExistingStack(connection, characterId, itemTemplateId, quantity)
                || InventoryStackMerge.insertRespectingMaxStack(connection, characterId, itemTemplateId, quantity);
    }

    private static Connection connection(Transaction tx) {
        return ((JdbcTransaction) tx).connection();
    }
}
