package dev.kage.server.database.inventory;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.inventory.domain.DismantlePolicy;
import dev.kage.inventory.domain.DismantlePort;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link DismantlePort} (ADR-097). Giao dịch: khoá túi chung ({@link CharacterInventoryLock}, DATABASE.md
 * §5.1) → khoá dòng món → kiểm (ô chiến đấu, không mặc, không khảm, không đang chào) → xoá → cộng Linh Thảo (gộp chồng,
 * không thì mở ô — luôn vừa vì vừa trả lại một ô) → cộng vàng qua {@link GoldLedger} → audit.
 */
public final class PostgresDismantleAdapter implements DismantlePort {

    private static final String GOLD_REASON = "gold.reason.dismantle";

    private static final String LOCK_ITEM = """
            SELECT it.equip_slot, it.required_level, inventory_item.enhance_level,
                   (EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = inventory_item.id)
                    OR inventory_item.locked) AS equipped, -- ADR-159: món khoá bị chặn như món đang mặc
                   EXISTS (SELECT 1 FROM item_socket s WHERE s.inventory_item_id = inventory_item.id) AS socketed,
            """ + TradeOfferedQuantity.OF_INVENTORY_ITEM + """
                    AS offered
              FROM inventory_item
              JOIN item_template it ON it.id = inventory_item.item_template_id
             WHERE inventory_item.id = ? AND inventory_item.character_id = ?
               FOR UPDATE OF inventory_item
            """;

    private static final String DELETE_ITEM = "DELETE FROM inventory_item WHERE id = ?";

    private static final String HERB_TEMPLATE = "SELECT id FROM item_template WHERE code = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private record LockedItem(
            String equipSlot, int requiredLevel, int enhanceLevel, boolean equipped, boolean socketed, int offered) {}

    private final DataSource dataSource;

    public PostgresDismantleAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Result dismantle(long characterId, long itemId) {
        return JdbcTransactionRunner.call(dataSource, connection -> dismantleIn(connection, characterId, itemId));
    }

    private static Result dismantleIn(Connection connection, long characterId, long itemId) {
        CharacterInventoryLock.acquire(connection, characterId);
        Optional<LockedItem> found = JdbcOperations.queryOne(
                connection,
                LOCK_ITEM,
                rs -> new LockedItem(
                        rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getBoolean(4), rs.getBoolean(5), rs.getInt(6)),
                itemId,
                characterId);
        if (found.isEmpty()) {
            return Result.refused(Outcome.NOT_FOUND);
        }
        LockedItem item = found.get();
        Optional<Outcome> refused = refusal(item);
        if (refused.isPresent()) {
            return Result.refused(refused.get());
        }
        JdbcOperations.update(connection, DELETE_ITEM, itemId);
        int herbs = DismantlePolicy.herbs(item.enhanceLevel());
        long herbTemplateId = JdbcOperations.queryOne(
                        connection, HERB_TEMPLATE, rs -> rs.getLong(1), DismantlePolicy.HERB_CODE)
                .orElseThrow();
        if (!InventoryStackMerge.mergeIntoExistingStack(connection, characterId, herbTemplateId, herbs)) {
            InventoryStackMerge.insertRespectingMaxStack(connection, characterId, herbTemplateId, herbs);
        }
        long gold = DismantlePolicy.gold(item.requiredLevel(), item.enhanceLevel());
        GoldLedger.credit(connection, characterId, gold, GOLD_REASON);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "ITEM_DISMANTLED",
                "{\"itemId\":" + itemId + ",\"enhance\":" + item.enhanceLevel() + ",\"gold\":" + gold + ",\"herbs\":"
                        + herbs + "}",
                Timestamp.from(Instant.now()));
        return new Result(Outcome.OK, gold, herbs);
    }

    private static Optional<Outcome> refusal(LockedItem item) {
        if (item.equipSlot() == null
                || !DismantlePolicy.DISMANTLABLE_SLOTS.contains(EquipmentSlotType.valueOf(item.equipSlot()))) {
            return Optional.of(Outcome.NOT_DISMANTLABLE);
        }
        if (item.equipped()) {
            return Optional.of(Outcome.EQUIPPED);
        }
        if (item.socketed()) {
            return Optional.of(Outcome.SOCKETED);
        }
        if (item.offered() > 0) {
            return Optional.of(Outcome.ITEM_IN_TRADE);
        }
        return Optional.empty();
    }
}
