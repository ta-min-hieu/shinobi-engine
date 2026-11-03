package dev.kage.server.database.crafting;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.SplittableRandom;

import dev.kage.crafting.domain.CraftingWorkshopPort;
import dev.kage.crafting.domain.Transaction;
import dev.kage.inventory.domain.RefinementPolicy;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;
import dev.kage.server.database.inventory.ItemLine;
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

    // Dưới khoá túi của chính nhân vật ⇒ dòng mới nhất của mẫu này là dòng vừa chèn (identity tăng dần).
    private static final String NEWEST_ROW =
            "SELECT MAX(id) FROM inventory_item WHERE character_id = ? AND item_template_id = ?";
    private static final String REQUIRED_LEVEL = "SELECT required_level FROM item_template WHERE id = ?";
    private static final String INSERT_LINE =
            "INSERT INTO item_refinement (inventory_item_id, line_index, stat, value) VALUES (?, ?, ?, ?)";

    private final SplittableRandom random = new SplittableRandom();

    @Override
    public void lockInventory(Transaction tx, long characterId) {
        CharacterInventoryLock.acquire(connection(tx), characterId);
    }

    @Override
    public boolean consumeMaterial(Transaction tx, long characterId, long itemTemplateId, int quantity) {
        return TemplateQuantityConsumer.consumeUnlocked( // ADR-159 — nguyên liệu đã khoá không bị đem luyện
                connection(tx), characterId, new ItemLine(itemTemplateId, quantity, 0));
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
    public boolean grantForged(Transaction tx, long characterId, long itemTemplateId, int refinementLines) {
        Connection connection = connection(tx);
        if (!InventoryStackMerge.insertRespectingMaxStack(connection, characterId, itemTemplateId, 1)) {
            return false;
        }
        if (refinementLines == 0) {
            return true;
        }
        long itemId = JdbcOperations.queryOne(connection, NEWEST_ROW, rs -> rs.getLong(1), characterId, itemTemplateId)
                .orElseThrow();
        int requiredLevel = JdbcOperations.queryOne(connection, REQUIRED_LEVEL, rs -> rs.getInt(1), itemTemplateId)
                .orElse(1);
        List<RefinementPolicy.Line> lines;
        synchronized (random) {
            lines = RefinementPolicy.seedLines(refinementLines, requiredLevel, random);
        }
        for (RefinementPolicy.Line line : lines) {
            JdbcOperations.update(
                    connection, INSERT_LINE, itemId, line.index(), line.stat().name(), line.value());
        }
        return true;
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
