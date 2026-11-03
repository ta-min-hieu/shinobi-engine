package dev.kage.server.database.trade;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.inventory.BagCapacity;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;
import dev.kage.trade.domain.OwnedItemSnapshot;
import dev.kage.trade.domain.TradeInventoryPort;
import dev.kage.trade.domain.Transaction;

/**
 * Hiện thực {@link TradeInventoryPort} trên PostgreSQL — SQL riêng cho {@code inventory_item},
 * bản sao ý tưởng {@code PostgresShopInventoryGatewayAdapter}. {@link #transferItem} là bước XÁC
 * THỰC THẬT DUY NHẤT của toàn bộ feature-trade (xem feature-trade/CLAUDE.md) — khoá dòng FOR
 * UPDATE, kiểm còn đúng chủ + đủ số lượng, gộp vào chồng sẵn có của người nhận hoặc tách stack.
 */
public final class PostgresTradeInventoryAdapter implements TradeInventoryPort {

    private static final String FIND_BY_ID = """
            SELECT id, character_id, item_template_id, quantity, enhance_level FROM inventory_item WHERE id = ?
            """;

    private static final String SELECT_FOR_UPDATE = """
            SELECT character_id, item_template_id, quantity, enhance_level FROM inventory_item WHERE id = ? FOR UPDATE
            """;

    // Đọc SAU khi dòng nguồn đã khoá FOR UPDATE ở cùng transaction — xem #transferItem. ADR-159: món người chơi
    // đã KHOÁ bị chặn y như món đang mặc.
    private static final String IS_EQUIPPED_BY = """
            SELECT (SELECT COUNT(*) FROM equipment_slot WHERE character_id = ? AND inventory_item_id = ?)
                 + (SELECT COUNT(*) FROM inventory_item WHERE id = ? AND locked)
            """;

    // Chỉ đếm món NẰM TRONG TÚI: đồ đang mặc không chiếm ô (quyết định chủ dự án 2026-10-11). Trước đó
    // túi 20 ô tính cả 7 món trên người, nên người mặc đủ bộ chỉ còn 13 ô thật.
    private static final String COUNT_ITEMS = """
            SELECT COUNT(*) FROM inventory_item ii
             WHERE ii.character_id = ?
               AND NOT EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = ii.id)
            """;

    private static final String UPDATE_OWNER = """
            UPDATE inventory_item SET character_id = ? WHERE id = ?
            """;

    private static final String DECREMENT_QUANTITY = """
            UPDATE inventory_item SET quantity = quantity - ? WHERE id = ?
            """;

    private static final String DELETE_ROW = """
            DELETE FROM inventory_item WHERE id = ?
            """;

    // ADR-160 — nối CUỐI túi người nhận (MAX + 1), cùng lý do InventoryStackMerge.INSERT_ITEM.
    private static final String INSERT_ROW = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
            VALUES (?, ?, ?, ?, (SELECT COALESCE(MAX(slot_index) + 1, 0) FROM inventory_item WHERE character_id = ?))
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
    public Optional<OwnedItemSnapshot> findItemById(Transaction tx, long itemId) {
        return JdbcOperations.queryOne(
                ((JdbcTransaction) tx).connection(), FIND_BY_ID, PostgresTradeInventoryAdapter::mapRow, itemId);
    }

    @Override
    public void lockInventories(Transaction tx, long characterIdA, long characterIdB) {
        CharacterInventoryLock.acquireBoth(((JdbcTransaction) tx).connection(), characterIdA, characterIdB);
    }

    @Override
    public TransferOutcome transferItem(Transaction tx, TransferRequest request) {
        Connection connection = ((JdbcTransaction) tx).connection();
        Optional<OwnedItemSnapshot> current = selectForUpdate(connection, request.itemId());
        Optional<TransferOutcome> rejection = rejectionOf(connection, request, current);
        if (rejection.isPresent()) {
            return rejection.get();
        }
        boolean isWholeRow = current.get().quantity() == request.quantity();
        if (mergeIntoRecipientStack(connection, request, current.get())) {
            if (isWholeRow) {
                JdbcOperations.update(connection, DELETE_ROW, request.itemId());
            } else {
                JdbcOperations.update(connection, DECREMENT_QUANTITY, request.quantity(), request.itemId());
            }
            return TransferOutcome.OK;
        }
        // Không kiểm sức chứa ở đây, kể cả khi tách dòng (sửa 2026-10-03): ConfirmTradeUseCase kiểm
        // #exceedsCapacity SAU mọi lượt. Kiểm giữa chừng làm giao dịch hợp lệ (người mời tách chồng
        // sang túi 20 dòng, người nhận đưa lại một dòng) thất bại hay thành công tuỳ ai là người mời.
        if (isWholeRow) {
            JdbcOperations.update(connection, UPDATE_OWNER, request.toCharacterId(), request.itemId());
        } else {
            JdbcOperations.update(connection, DECREMENT_QUANTITY, request.quantity(), request.itemId());
            insertRow(connection, request, current.get());
        }
        return TransferOutcome.OK;
    }

    /**
     * Lý do KHÔNG được chuyển dòng đã khoá {@code current}, rỗng nếu chuyển được. Tách khỏi
     * {@link #transferItem} để giữ độ phức tạp dưới trần PMD.
     *
     * <p>Món đang mặc KHÔNG được rời túi qua giao dịch (sửa 2026-09-24): trước đó nó đổi chủ nguyên
     * dòng trong khi ô trang bị người đưa vẫn trỏ vào nó — người đưa giữ chỉ số trong World (chỉ
     * tính lại khi mặc/gỡ/cường hoá), người nhận mặc được ngay: một món, hai người hưởng.
     */
    private static Optional<TransferOutcome> rejectionOf(
            Connection connection, TransferRequest request, Optional<OwnedItemSnapshot> current) {
        if (current.isEmpty() || current.get().characterId() != request.fromCharacterId()) {
            return Optional.of(TransferOutcome.ITEM_NOT_OWNED);
        }
        if (current.get().quantity() < request.quantity()) {
            return Optional.of(TransferOutcome.INSUFFICIENT_QUANTITY);
        }
        if (isEquippedBy(connection, request.fromCharacterId(), request.itemId())) {
            return Optional.of(TransferOutcome.ITEM_EQUIPPED);
        }
        return Optional.empty();
    }

    /**
     * Gộp vào chồng sẵn có của người nhận (chủ dự án đảo quyết định MVP 2 ngày 2026-09-24 — giao
     * dịch nay gộp chồng như rơi đồ/cửa hàng/quest/thư). Chỉ đồ CHƯA cường hoá: đồ đã cường hoá là
     * một cá thể riêng, không được hoà vào chồng thường ({@link InventoryStackMerge} cũng chỉ nhận
     * chồng {@code enhance_level = 0} ở phía đích).
     *
     * <p>Thứ tự khoá không đổi so với trước: dòng nguồn đã khoá {@code FOR UPDATE} ở trên, dòng
     * chồng đích bị {@code UPDATE} khoá SAU — cùng chiều "vàng → túi đồ" với cửa hàng/thư, không
     * tạo vòng khoá mới.
     */
    private static boolean mergeIntoRecipientStack(
            Connection connection, TransferRequest request, OwnedItemSnapshot source) {
        return source.enhanceLevel() == 0
                && InventoryStackMerge.mergeIntoExistingStack(
                        connection, request.toCharacterId(), source.itemTemplateId(), request.quantity());
    }

    @Override
    public boolean exceedsCapacity(Transaction tx, long characterId) {
        Connection connection = ((JdbcTransaction) tx).connection();
        return countItems(connection, characterId) > BagCapacity.of(connection, characterId);
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

    private static boolean isEquippedBy(Connection connection, long characterId, long itemId) {
        return JdbcOperations.queryOne(connection, IS_EQUIPPED_BY, rs -> rs.getInt(1), characterId, itemId, itemId)
                        .orElse(0)
                > 0;
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
                request.toCharacterId());
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
