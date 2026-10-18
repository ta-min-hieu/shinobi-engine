package dev.kage.server.database.market;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.market.domain.ListingDraft;
import dev.kage.market.domain.MarketListing;
import dev.kage.market.domain.MarketPort;
import dev.kage.market.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link MarketPort} (ADR-063, V65). Mọi chuyển trạng thái là compare-and-set trên {@code status}. */
public final class PostgresMarketAdapter implements MarketPort {

    private static final String COLUMNS = """
            SELECT ml.id, ml.seller_id, c.name AS seller_name, ml.item_template_id, ml.quantity, ml.price,
                   ml.expires_at
              FROM market_listing ml JOIN character c ON c.id = ml.seller_id
            """;

    private static final String LOCK_ACTIVE = COLUMNS + """
             WHERE ml.id = ? AND ml.status = 'ACTIVE' AND ml.expires_at > ?
               FOR UPDATE OF ml
            """;

    private static final String COUNT_ACTIVE =
            "SELECT COUNT(*) FROM market_listing WHERE seller_id = ? AND status = 'ACTIVE'";

    private static final String INSERT = """
            INSERT INTO market_listing
                (seller_id, item_template_id, quantity, price, status, listed_at, expires_at)
            VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
            RETURNING id
            """;

    private static final String MARK_SOLD = """
            UPDATE market_listing SET status = 'SOLD', buyer_id = ?, closed_at = ?
             WHERE id = ? AND status = 'ACTIVE'
            """;

    private static final String MARK_CANCELLED = """
            UPDATE market_listing SET status = 'CANCELLED', closed_at = now()
             WHERE id = ? AND status = 'ACTIVE'
            """;

    private static final String BROWSE_ALL = COLUMNS + """
             WHERE ml.status = 'ACTIVE' AND ml.expires_at > ?
             ORDER BY ml.listed_at DESC, ml.id DESC LIMIT ?
            """;

    private static final String BROWSE_ITEM = COLUMNS + """
             WHERE ml.status = 'ACTIVE' AND ml.expires_at > ? AND ml.item_template_id = ?
             ORDER BY ml.listed_at DESC, ml.id DESC LIMIT ?
            """;

    private static final String BY_SELLER = COLUMNS + """
             WHERE ml.status = 'ACTIVE' AND ml.expires_at > ? AND ml.seller_id = ?
             ORDER BY ml.listed_at DESC, ml.id DESC
            """;

    private static final String EXPIRE_DUE = """
            UPDATE market_listing SET status = 'EXPIRED', closed_at = now()
             WHERE status = 'ACTIVE' AND expires_at <= ?
            """;

    private static final String UNRETURNED = COLUMNS + """
             WHERE ml.status = 'EXPIRED' AND NOT ml.returned ORDER BY ml.id LIMIT ?
            """;

    private static final String MARK_RETURNED = "UPDATE market_listing SET returned = TRUE WHERE id = ?";

    private final DataSource dataSource;

    public PostgresMarketAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<MarketListing> lockActive(Transaction tx, long listingId, Instant now) {
        return JdbcOperations.queryOne(
                connection(tx), LOCK_ACTIVE, PostgresMarketAdapter::row, listingId, Timestamp.from(now));
    }

    @Override
    public int countActive(Transaction tx, long sellerId) {
        return JdbcOperations.queryOne(connection(tx), COUNT_ACTIVE, rs -> rs.getInt(1), sellerId)
                .orElse(0);
    }

    @Override
    public long insert(Transaction tx, ListingDraft draft) {
        return JdbcOperations.insertReturningId(
                connection(tx),
                INSERT,
                draft.sellerId(),
                draft.itemTemplateId(),
                draft.quantity(),
                draft.price(),
                Timestamp.from(Instant.now()),
                Timestamp.from(draft.expiresAt()));
    }

    @Override
    public void markSold(Transaction tx, long listingId, long buyerId, Instant at) {
        JdbcOperations.update(connection(tx), MARK_SOLD, buyerId, Timestamp.from(at), listingId);
    }

    @Override
    public void markCancelled(Transaction tx, long listingId) {
        JdbcOperations.update(connection(tx), MARK_CANCELLED, listingId);
    }

    @Override
    public List<MarketListing> browse(Optional<Long> itemTemplateId, Instant now, int limit) {
        return itemTemplateId.isPresent()
                ? JdbcOperations.query(
                        dataSource,
                        BROWSE_ITEM,
                        PostgresMarketAdapter::row,
                        Timestamp.from(now),
                        itemTemplateId.get(),
                        limit)
                : JdbcOperations.query(dataSource, BROWSE_ALL, PostgresMarketAdapter::row, Timestamp.from(now), limit);
    }

    @Override
    public List<MarketListing> listActiveBySeller(long sellerId, Instant now) {
        return JdbcOperations.query(dataSource, BY_SELLER, PostgresMarketAdapter::row, Timestamp.from(now), sellerId);
    }

    @Override
    public int expireDue(Instant now) {
        return JdbcOperations.update(dataSource, EXPIRE_DUE, Timestamp.from(now));
    }

    @Override
    public List<MarketListing> unreturnedExpired(int limit) {
        return JdbcOperations.query(dataSource, UNRETURNED, PostgresMarketAdapter::row, limit);
    }

    @Override
    public void markReturned(long listingId) {
        JdbcOperations.update(dataSource, MARK_RETURNED, listingId);
    }

    private static MarketListing row(ResultSet rs) throws SQLException {
        return new MarketListing(
                rs.getLong("id"),
                rs.getLong("seller_id"),
                rs.getString("seller_name"),
                rs.getLong("item_template_id"),
                rs.getInt("quantity"),
                rs.getLong("price"),
                rs.getTimestamp("expires_at").toInstant());
    }

    private static Connection connection(Transaction tx) {
        return ((JdbcTransaction) tx).connection();
    }
}
