package dev.kage.server.database.shop;

import dev.kage.server.database.JdbcOperations;
import dev.kage.shop.domain.ShopCatalogPort;
import dev.kage.shop.domain.ShopListing;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/** Hiện thực {@link ShopCatalogPort} trên PostgreSQL — đọc thuần, {@code shop_item} là config tĩnh. */
public final class PostgresShopCatalogAdapter implements ShopCatalogPort {

    private static final String FIND_BY_TEMPLATE = """
            SELECT item_template_id, buy_price, sell_price FROM shop_item WHERE item_template_id = ?
            """;

    private static final String FIND_ALL = """
            SELECT item_template_id, buy_price, sell_price FROM shop_item ORDER BY item_template_id
            """;

    private final DataSource dataSource;

    public PostgresShopCatalogAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<ShopListing> findListing(long itemTemplateId) {
        return JdbcOperations.queryOne(
                dataSource, FIND_BY_TEMPLATE, PostgresShopCatalogAdapter::toListing, itemTemplateId);
    }

    @Override
    public List<ShopListing> findAll() {
        return JdbcOperations.query(dataSource, FIND_ALL, PostgresShopCatalogAdapter::toListing);
    }

    private static ShopListing toListing(ResultSet rs) throws SQLException {
        return new ShopListing(rs.getLong("item_template_id"), rs.getLong("buy_price"), rs.getLong("sell_price"));
    }
}
