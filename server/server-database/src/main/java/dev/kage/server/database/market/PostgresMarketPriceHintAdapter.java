package dev.kage.server.database.market;

import java.sql.Timestamp;
import java.time.Instant;

import javax.sql.DataSource;

import dev.kage.market.domain.MarketPriceHintPort;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link MarketPriceHintPort} (ADR-234) — một câu gộp trên {@code market_listing}, giá chia theo số lượng.
 */
public final class PostgresMarketPriceHintAdapter implements MarketPriceHintPort {

    private static final String HINT = """
            SELECT COALESCE(MIN(price / quantity) FILTER (WHERE status = 'ACTIVE'), 0) AS lowest,
                   COALESCE(FLOOR(AVG(price::numeric / quantity) FILTER (WHERE status = 'SOLD' AND closed_at >= ?)), 0)
                       AS average,
                   COUNT(*) FILTER (WHERE status = 'SOLD' AND closed_at >= ?) AS sold
              FROM market_listing
             WHERE item_template_id = ? AND quantity > 0
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresMarketPriceHintAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Hint hint(long itemTemplateId, Instant since) {
        Timestamp from = Timestamp.from(since);
        return JdbcOperations.queryOne(
                        dataSource,
                        HINT,
                        rs -> new Hint(rs.getLong("lowest"), rs.getLong("average"), rs.getInt("sold")),
                        from,
                        from,
                        itemTemplateId)
                .orElse(Hint.NONE);
    }
}
