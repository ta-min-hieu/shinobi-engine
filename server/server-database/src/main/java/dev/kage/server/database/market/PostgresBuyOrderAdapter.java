package dev.kage.server.database.market;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.market.domain.BuyOrder;
import dev.kage.market.domain.BuyOrderPort;
import dev.kage.market.domain.NewBuyOrder;
import dev.kage.market.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link BuyOrderPort} (ADR-116, V96). Mọi chuyển trạng thái là compare-and-set trên {@code status}. */
public final class PostgresBuyOrderAdapter implements BuyOrderPort {

    private static final String COLUMNS = """
            SELECT id, buyer_id, item_template_id, quantity_wanted, quantity_filled, unit_price, expires_at
              FROM market_buy_order
            """;

    private static final String TEMPLATE_EXISTS = "SELECT 1 FROM item_template WHERE id = ?";

    private static final String COUNT_ACTIVE =
            "SELECT COUNT(*) FROM market_buy_order WHERE buyer_id = ? AND status = 'ACTIVE'";

    private static final String INSERT = """
            INSERT INTO market_buy_order
                (buyer_id, item_template_id, quantity_wanted, unit_price, status, created_at, expires_at)
            VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
            RETURNING id
            """;

    private static final String BUYER_OF = "SELECT buyer_id FROM market_buy_order WHERE id = ?";

    private static final String LOCK_ACTIVE =
            COLUMNS + " WHERE id = ? AND status = 'ACTIVE' AND expires_at > ? FOR UPDATE";

    private static final String ADD_FILLED = """
            UPDATE market_buy_order
               SET quantity_filled = quantity_filled + ?,
                   status = CASE WHEN quantity_filled + ? >= quantity_wanted THEN 'FILLED' ELSE status END,
                   closed_at = CASE WHEN quantity_filled + ? >= quantity_wanted THEN ? ELSE closed_at END
             WHERE id = ? AND status = 'ACTIVE'
            """;

    private static final String MARK_CANCELLED =
            "UPDATE market_buy_order SET status = 'CANCELLED', closed_at = ? WHERE id = ? AND status = 'ACTIVE'";

    private static final String BROWSE_ALL = COLUMNS + """
             WHERE status = 'ACTIVE' AND expires_at > ?
             ORDER BY unit_price DESC, id LIMIT ?
            """;

    private static final String BROWSE_ITEM = COLUMNS + """
             WHERE status = 'ACTIVE' AND expires_at > ? AND item_template_id = ?
             ORDER BY unit_price DESC, id LIMIT ?
            """;

    private static final String MINE = COLUMNS + """
             WHERE status = 'ACTIVE' AND expires_at > ? AND buyer_id = ?
             ORDER BY id DESC
            """;

    private static final String EXPIRE_DUE = """
            UPDATE market_buy_order SET status = 'EXPIRED', closed_at = now()
             WHERE status = 'ACTIVE' AND expires_at <= ?
            """;

    private static final String UNREFUNDED = COLUMNS + " WHERE status = 'EXPIRED' AND NOT refunded ORDER BY id LIMIT ?";

    private static final String MARK_REFUNDED = "UPDATE market_buy_order SET refunded = TRUE WHERE id = ?";

    private final DataSource dataSource;

    public PostgresBuyOrderAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean templateExists(Transaction tx, long itemTemplateId) {
        return JdbcOperations.queryOne(connection(tx), TEMPLATE_EXISTS, rs -> Boolean.TRUE, itemTemplateId)
                .isPresent();
    }

    @Override
    public int countActive(Transaction tx, long buyerId) {
        return JdbcOperations.queryOne(connection(tx), COUNT_ACTIVE, rs -> rs.getInt(1), buyerId)
                .orElse(0);
    }

    @Override
    public long insert(Transaction tx, NewBuyOrder order) {
        return JdbcOperations.queryOne(
                        connection(tx),
                        INSERT,
                        rs -> rs.getLong(1),
                        order.buyerId(),
                        order.itemTemplateId(),
                        order.quantity(),
                        order.unitPrice(),
                        Timestamp.from(order.createdAt()),
                        Timestamp.from(order.expiresAt()))
                .orElseThrow();
    }

    @Override
    public Optional<Long> buyerOf(Transaction tx, long orderId) {
        return JdbcOperations.queryOne(connection(tx), BUYER_OF, rs -> rs.getLong(1), orderId);
    }

    @Override
    public Optional<BuyOrder> lockActive(Transaction tx, long orderId, Instant now) {
        return JdbcOperations.queryOne(
                connection(tx), LOCK_ACTIVE, PostgresBuyOrderAdapter::row, orderId, Timestamp.from(now));
    }

    @Override
    public void addFilled(Transaction tx, long orderId, int quantity, Instant now) {
        JdbcOperations.update(connection(tx), ADD_FILLED, quantity, quantity, quantity, Timestamp.from(now), orderId);
    }

    @Override
    public void markCancelled(Transaction tx, long orderId, Instant now) {
        JdbcOperations.update(connection(tx), MARK_CANCELLED, Timestamp.from(now), orderId);
    }

    @Override
    public List<BuyOrder> browse(Optional<Long> itemTemplateId, Instant now, int limit) {
        return itemTemplateId
                .map(id -> JdbcOperations.query(
                        dataSource, BROWSE_ITEM, PostgresBuyOrderAdapter::row, Timestamp.from(now), id, limit))
                .orElseGet(() -> JdbcOperations.query(
                        dataSource, BROWSE_ALL, PostgresBuyOrderAdapter::row, Timestamp.from(now), limit));
    }

    @Override
    public List<BuyOrder> mine(long buyerId, Instant now) {
        return JdbcOperations.query(dataSource, MINE, PostgresBuyOrderAdapter::row, Timestamp.from(now), buyerId);
    }

    @Override
    public void expireDue(Instant now) {
        JdbcOperations.update(dataSource, EXPIRE_DUE, Timestamp.from(now));
    }

    @Override
    public List<BuyOrder> unrefundedExpired(int limit) {
        return JdbcOperations.query(dataSource, UNREFUNDED, PostgresBuyOrderAdapter::row, limit);
    }

    @Override
    public void markRefunded(long orderId) {
        JdbcOperations.update(dataSource, MARK_REFUNDED, orderId);
    }

    private static BuyOrder row(ResultSet rs) throws SQLException {
        return new BuyOrder(
                rs.getLong("id"),
                rs.getLong("buyer_id"),
                rs.getLong("item_template_id"),
                rs.getInt("quantity_wanted"),
                rs.getInt("quantity_filled"),
                rs.getLong("unit_price"),
                rs.getTimestamp("expires_at").toInstant());
    }

    private static Connection connection(Transaction tx) {
        return ((JdbcTransaction) tx).connection();
    }
}
