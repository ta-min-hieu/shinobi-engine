package dev.kage.server.database.inventory;

import java.sql.Connection;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

import javax.sql.DataSource;

import dev.kage.inventory.domain.AutoSellJunkPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.server.database.GoldCapExceededException;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** ADR-204 — {@code character.auto_sell_junk}; bán theo {@code shop_item.sell_price}, qua {@code GoldLedger}. */
public final class PostgresAutoSellJunkAdapter implements AutoSellJunkPort {

    private static final String GOLD_REASON = "gold.reason.auto_sell_junk";
    private static final String ENABLED = "SELECT auto_sell_junk FROM character WHERE id = ?";
    private static final String SET = "UPDATE character SET auto_sell_junk = ? WHERE id = ?";
    // Chỉ món rác CÓ giá thu — món rác không bán được thì vẫn vào túi như thường (không bán 0 vàng).
    private static final String JUNK = """
            SELECT it.id FROM item_template it JOIN shop_item si ON si.item_template_id = it.id
             WHERE it.junk AND si.sell_price > 0
            """;
    private static final String PRICE = "SELECT sell_price FROM shop_item WHERE item_template_id = ?";
    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, 'LOOT_AUTO_SOLD', ?, now())
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresAutoSellJunkAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean enabled(long characterId) {
        return JdbcOperations.queryOne(dataSource, ENABLED, rs -> rs.getBoolean(1), characterId)
                .orElse(false);
    }

    @Override
    public void setEnabled(long characterId, boolean enabled) {
        JdbcOperations.update(dataSource, SET, enabled, characterId);
    }

    @Override
    public Set<Long> junkTemplates() {
        return Set.copyOf(new HashSet<>(JdbcOperations.query(dataSource, JUNK, rs -> rs.getLong(1))));
    }

    @Override
    public OptionalLong sell(long characterId, List<ItemStack> stacks) {
        try {
            return OptionalLong.of(
                    JdbcTransactionRunner.call(dataSource, connection -> sellIn(connection, characterId, stacks)));
        } catch (GoldCapExceededException e) {
            return OptionalLong.empty();
        }
    }

    private static long sellIn(Connection connection, long characterId, List<ItemStack> stacks) {
        long total = 0L;
        StringBuilder detail = new StringBuilder("{\"items\":[");
        for (ItemStack stack : stacks) {
            long price = JdbcOperations.queryOne(connection, PRICE, rs -> rs.getLong(1), stack.itemTemplateId())
                    .orElse(0L);
            total += price * stack.quantity();
            if (detail.charAt(detail.length() - 1) != '[') {
                detail.append(',');
            }
            detail.append("{\"itemTemplateId\":")
                    .append(stack.itemTemplateId())
                    .append(",\"quantity\":")
                    .append(stack.quantity())
                    .append('}');
        }
        detail.append("],\"gold\":").append(total).append('}');
        if (total > 0L) {
            GoldLedger.credit(connection, characterId, total, GOLD_REASON);
        }
        JdbcOperations.update(connection, AUDIT, characterId, detail.toString());
        return total;
    }
}
