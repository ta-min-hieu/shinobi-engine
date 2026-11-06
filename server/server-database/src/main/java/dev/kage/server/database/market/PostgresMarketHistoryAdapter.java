package dev.kage.server.database.market;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.market.domain.MarketHistoryPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link MarketHistoryPort} (ADR-177; chỉ mục V141). */
public final class PostgresMarketHistoryAdapter implements MarketHistoryPort {

    // Hai nhánh UNION ALL để mỗi nhánh dùng đúng chỉ mục một phần của mình (bán / mua).
    private static final String RECENT = """
            SELECT * FROM (
                SELECT l.id, TRUE AS sold, l.item_template_id, l.quantity, l.price, COALESCE(c.name, ''), l.closed_at
                  FROM market_listing l LEFT JOIN character c ON c.id = l.buyer_id
                 WHERE l.status = 'SOLD' AND l.seller_id = ? AND l.closed_at >= ?
                UNION ALL
                SELECT l.id, FALSE AS sold, l.item_template_id, l.quantity, l.price, COALESCE(c.name, ''), l.closed_at
                  FROM market_listing l LEFT JOIN character c ON c.id = l.seller_id
                 WHERE l.status = 'SOLD' AND l.buyer_id = ? AND l.closed_at >= ?
            ) history
            ORDER BY closed_at DESC, id DESC
            LIMIT ?
            """;

    private final DataSource dataSource;

    public PostgresMarketHistoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Entry> recent(long characterId, Instant since, int limit) {
        Timestamp from = Timestamp.from(since);
        return JdbcOperations.query(
                dataSource,
                RECENT,
                rs -> new Entry(
                        rs.getLong(1),
                        rs.getBoolean(2),
                        rs.getLong(3),
                        rs.getInt(4),
                        rs.getLong(5),
                        rs.getString(6),
                        rs.getTimestamp(7).toInstant()),
                characterId,
                from,
                characterId,
                from,
                limit);
    }
}
