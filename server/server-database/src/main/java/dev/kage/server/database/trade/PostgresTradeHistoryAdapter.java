package dev.kage.server.database.trade;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcOperations;
import dev.kage.trade.domain.TradeHistoryPort;

/**
 * Hiện thực {@link TradeHistoryPort} (ADR-189; chỉ mục V149) — đọc dòng audit {@code TRADE_EXECUTED} (JSON do
 * {@code ConfirmTradeUseCase.logAudit} ghi) bằng toán tử jsonb của Postgres, không cần thư viện JSON phía Java.
 */
public final class PostgresTradeHistoryAdapter implements TradeHistoryPort {

    private static final String TRADES = """
            SELECT a.id, COALESCE(c.name, ''), (a.detail_json::jsonb ->> 'goldGiven')::bigint,
                   (a.detail_json::jsonb ->> 'goldReceived')::bigint, a.created_at
              FROM audit_log a
              LEFT JOIN character c ON c.id = (a.detail_json::jsonb ->> 'counterpartId')::bigint
             WHERE a.character_id = ? AND a.action = 'TRADE_EXECUTED' AND a.created_at >= ?
             ORDER BY a.created_at DESC, a.id DESC
             LIMIT ?
            """;

    // Món của các dòng vừa đọc: "G" = đã đưa, "R" = đã nhận.
    private static final String ITEMS = """
            SELECT a.id, side.dir, (item ->> 'itemTemplateId')::bigint, (item ->> 'quantity')::int,
                   (item ->> 'enhanceLevel')::int
              FROM audit_log a
             CROSS JOIN LATERAL (VALUES ('G', a.detail_json::jsonb -> 'itemsGiven'),
                                        ('R', a.detail_json::jsonb -> 'itemsReceived')) AS side(dir, items)
             CROSS JOIN LATERAL jsonb_array_elements(side.items) AS item
             WHERE a.id = ANY (?)
             ORDER BY a.id
            """;

    private final DataSource dataSource;

    public PostgresTradeHistoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Dòng giao dịch chưa kèm món. */
    private record Trade(long auditId, String counterpart, long goldGiven, long goldReceived, Instant at) {}

    /** Một món kèm dòng giao dịch và chiều của nó. */
    private record ItemRow(long auditId, boolean given, Item item) {}

    @Override
    public List<Entry> recent(long characterId, Instant since, int limit) {
        List<Trade> trades = JdbcOperations.query(
                dataSource,
                TRADES,
                rs -> new Trade(
                        rs.getLong(1),
                        rs.getString(2),
                        rs.getLong(3),
                        rs.getLong(4),
                        rs.getTimestamp(5).toInstant()),
                characterId,
                Timestamp.from(since),
                limit);
        if (trades.isEmpty()) {
            return List.of();
        }
        Long[] ids = trades.stream().map(Trade::auditId).toArray(Long[]::new);
        Map<Long, List<Item>> given = new HashMap<>();
        Map<Long, List<Item>> received = new HashMap<>();
        for (ItemRow row : JdbcOperations.query(
                dataSource,
                ITEMS,
                rs -> new ItemRow(
                        rs.getLong(1),
                        "G".equals(rs.getString(2)),
                        new Item(rs.getLong(3), rs.getInt(4), rs.getInt(5))),
                (Object) ids)) {
            (row.given() ? given : received)
                    .computeIfAbsent(row.auditId(), id -> new ArrayList<>())
                    .add(row.item());
        }
        return trades.stream()
                .map(t -> new Entry(
                        t.counterpart(),
                        t.goldGiven(),
                        t.goldReceived(),
                        given.getOrDefault(t.auditId(), List.of()),
                        received.getOrDefault(t.auditId(), List.of()),
                        t.at()))
                .toList();
    }
}
