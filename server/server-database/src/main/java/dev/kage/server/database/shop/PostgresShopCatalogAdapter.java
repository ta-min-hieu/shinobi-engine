package dev.kage.server.database.shop;

import dev.kage.server.database.DatabaseAccessException;
import dev.kage.shop.domain.ShopCatalogPort;
import dev.kage.shop.domain.ShopListing;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_BY_TEMPLATE)) {
            statement.setLong(1, itemTemplateId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(toListing(rs));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đọc shop_item: itemTemplateId=" + itemTemplateId, e);
        }
    }

    @Override
    public List<ShopListing> findAll() {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_ALL);
                var rs = statement.executeQuery()) {
            var listings = new ArrayList<ShopListing>();
            while (rs.next()) {
                listings.add(toListing(rs));
            }
            return listings;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đọc danh mục shop_item", e);
        }
    }

    private static ShopListing toListing(ResultSet rs) throws SQLException {
        return new ShopListing(rs.getLong("item_template_id"), rs.getLong("buy_price"), rs.getLong("sell_price"));
    }
}
