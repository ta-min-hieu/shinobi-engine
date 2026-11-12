package dev.kage.server.database.inventory;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.inventory.domain.ItemLookupPort;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link ItemLookupPort} (ADR-279) — {@code shop_item} + {@code drop_table_entry} ⋈ {@code monster_template}.
 */
public final class PostgresItemLookupAdapter implements ItemLookupPort {

    private static final String PRICES = "SELECT buy_price, sell_price FROM shop_item WHERE item_template_id = ?";
    private static final String DROPS = """
            SELECT m.code, m.name_key, MAX(d.drop_chance_percent) AS chance
              FROM drop_table_entry d JOIN monster_template m ON m.drop_table_id = d.drop_table_id
             WHERE d.item_template_id = ?
             GROUP BY m.code, m.name_key
             ORDER BY chance DESC, m.code
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresItemLookupAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Lookup lookup(long itemTemplateId) {
        long[] prices = JdbcOperations.queryOne(
                        dataSource,
                        PRICES,
                        rs -> new long[] {rs.getLong("buy_price"), rs.getLong("sell_price")},
                        itemTemplateId)
                .orElse(new long[] {0L, 0L});
        List<DropSource> drops = JdbcOperations.query(
                dataSource,
                DROPS,
                rs -> new DropSource(rs.getString("code"), rs.getString("name_key"), rs.getInt("chance")),
                itemTemplateId);
        return new Lookup(prices[0], prices[1], drops);
    }
}
