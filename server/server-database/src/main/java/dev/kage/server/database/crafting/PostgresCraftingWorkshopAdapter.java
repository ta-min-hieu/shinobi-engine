package dev.kage.server.database.crafting;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;

import dev.kage.crafting.domain.CraftingWorkshopPort;
import dev.kage.crafting.domain.Transaction;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;
import dev.kage.server.database.inventory.TemplateQuantityConsumer;

/**
 * Hiện thực {@link CraftingWorkshopPort} (ADR-079 D3) — cùng các mảnh SQL mà thư dùng: khoá túi đồ
 * ({@link CharacterInventoryLock}), tiêu theo LOẠI qua nhiều chồng ({@link TemplateQuantityConsumer} — bỏ qua món đang
 * mặc, đã cường hoá, đã khảm, linh thú, phần đang chào giao dịch), cấp có gộp chồng ({@link InventoryStackMerge}), vàng
 * qua {@link GoldLedger} (điểm ghi DUY NHẤT, phát {@code GoldChangedEvent}).
 */
public final class PostgresCraftingWorkshopAdapter implements CraftingWorkshopPort {

    private static final String REASON_CRAFT = "gold.reason.craft";

    private static final String INSERT_AUDIT_LOG = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    @Override
    public void lockInventory(Transaction tx, long characterId) {
        CharacterInventoryLock.acquire(connection(tx), characterId);
    }

    @Override
    public boolean consumeMaterial(Transaction tx, long characterId, long itemTemplateId, int quantity) {
        return TemplateQuantityConsumer.consume(connection(tx), characterId, itemTemplateId, quantity);
    }

    @Override
    public boolean deductGold(Transaction tx, long characterId, long amount) {
        return GoldLedger.deduct(connection(tx), characterId, amount, REASON_CRAFT);
    }

    @Override
    public boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity) {
        Connection connection = connection(tx);
        return InventoryStackMerge.mergeIntoExistingStack(connection, characterId, itemTemplateId, quantity)
                || InventoryStackMerge.insertRespectingMaxStack(connection, characterId, itemTemplateId, quantity);
    }

    @Override
    public void audit(Transaction tx, long characterId, String action, String detailJson) {
        JdbcOperations.update(
                connection(tx), INSERT_AUDIT_LOG, characterId, action, detailJson, Timestamp.from(Instant.now()));
    }

    private static Connection connection(Transaction tx) {
        return ((JdbcCraftingTransactionRunner.JdbcCraftingTransaction) tx).connection();
    }
}
