package dev.kage.server.database.party;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.party.domain.PartyGoldSplitPort;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link PartyGoldSplitPort} (ADR-278). Một transaction: trừ người chia qua {@link GoldLedger} (không đủ ⇒
 * dừng, chưa ghi gì), cộng từng người nhận, ghi audit {@code PARTY_GOLD_SPLIT} cho người chia và {@code
 * PARTY_GOLD_RECEIVED} cho mỗi người nhận. Không có bảng mới.
 */
public final class PostgresPartyGoldSplitAdapter implements PartyGoldSplitPort {

    private static final String REASON_GIVE = "gold.reason.party_split";
    private static final String REASON_RECEIVE = "gold.reason.party_split_received";

    private static final String BALANCE = "SELECT gold FROM character_stat WHERE character_id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresPartyGoldSplitAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Split split(long giverId, List<Long> recipients, long each, Instant at) {
        long total = Math.multiplyExact(each, recipients.size());
        return JdbcTransactionRunner.call(dataSource, connection -> {
            if (!GoldLedger.deduct(connection, giverId, total, REASON_GIVE)) {
                return new Split(Outcome.NOT_ENOUGH_GOLD, 0L);
            }
            Timestamp when = Timestamp.from(at);
            for (long recipient : recipients) {
                GoldLedger.credit(connection, recipient, each, REASON_RECEIVE);
                JdbcOperations.update(
                        connection,
                        AUDIT,
                        recipient,
                        "PARTY_GOLD_RECEIVED",
                        "{\"from\":" + giverId + ",\"gold\":" + each + "}",
                        when);
            }
            JdbcOperations.update(
                    connection,
                    AUDIT,
                    giverId,
                    "PARTY_GOLD_SPLIT",
                    "{\"recipients\":" + recipients + ",\"each\":" + each + ",\"total\":" + total + "}",
                    when);
            long after = JdbcOperations.queryOne(connection, BALANCE, rs -> rs.getLong(1), giverId)
                    .orElse(0L);
            return new Split(Outcome.OK, after);
        });
    }
}
