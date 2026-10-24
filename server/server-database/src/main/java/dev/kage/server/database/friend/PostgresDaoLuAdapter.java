package dev.kage.server.database.friend;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.friend.domain.DaoLuOutcome;
import dev.kage.friend.domain.DaoLuPort;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link DaoLuPort} (ADR-094, V85). Thứ tự trong giao dịch kết đạo lữ: khoá dòng {@code friendship} của cặp
 * → kiểm thân mật → chèn HAI dòng (khoá chính chặn người đã có đạo lữ) → trừ vàng người cầu qua {@link GoldLedger} →
 * audit. Từ chối giữa chừng ném {@link Refused} để rollback cả hai dòng.
 */
public final class PostgresDaoLuAdapter implements DaoLuPort {

    private static final String GOLD_REASON = "gold.reason.dao_lu";

    private static final String PARTNER_OF = "SELECT partner_id FROM dao_lu WHERE character_id = ?";

    private static final String LOCK_FRIENDSHIP = """
            SELECT intimacy FROM friendship
             WHERE status = 'ACCEPTED'
               AND ((requester_id = ? AND addressee_id = ?) OR (requester_id = ? AND addressee_id = ?))
               FOR UPDATE
            """;

    private static final String INTIMACY = """
            SELECT intimacy FROM friendship
             WHERE status = 'ACCEPTED'
               AND ((requester_id = ? AND addressee_id = ?) OR (requester_id = ? AND addressee_id = ?))
            """;

    private static final String INSERT = """
            INSERT INTO dao_lu (character_id, partner_id) VALUES (?, ?), (?, ?) ON CONFLICT DO NOTHING
            """;

    private static final String DELETE_OF = """
            DELETE FROM dao_lu WHERE character_id = ? OR partner_id = ? RETURNING character_id, partner_id
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Hai dòng mỗi cặp — chèn được ít hơn nghĩa là một trong hai đã có đạo lữ. */
    private static final int ROWS_PER_PAIR = 2;

    private final DataSource dataSource;

    public PostgresDaoLuAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Long> partnerOf(long characterId) {
        return JdbcOperations.queryOne(dataSource, PARTNER_OF, rs -> rs.getLong(1), characterId);
    }

    /** Một lượt kết đạo lữ — gom tham số cho giao dịch. */
    private record Bond(long proposerId, long accepterId, int minIntimacy, long cost) {}

    @Override
    public DaoLuOutcome bind(long proposerId, long accepterId, int minIntimacy, long cost) {
        Bond bond = new Bond(proposerId, accepterId, minIntimacy, cost);
        try {
            return JdbcTransactionRunner.call(dataSource, connection -> bindIn(connection, bond));
        } catch (Refused refused) {
            return refused.outcome;
        }
    }

    private static DaoLuOutcome bindIn(Connection connection, Bond bond) {
        int intimacy = JdbcOperations.queryOne(
                        connection,
                        LOCK_FRIENDSHIP,
                        rs -> rs.getInt(1),
                        bond.proposerId(),
                        bond.accepterId(),
                        bond.accepterId(),
                        bond.proposerId())
                .orElse(0);
        if (intimacy < bond.minIntimacy()) {
            return DaoLuOutcome.INTIMACY_TOO_LOW;
        }
        if (JdbcOperations.update(
                        connection, INSERT, bond.proposerId(), bond.accepterId(), bond.accepterId(), bond.proposerId())
                < ROWS_PER_PAIR) {
            throw new Refused(DaoLuOutcome.ALREADY_BOUND); // rollback dòng có thể đã chèn được
        }
        if (!GoldLedger.deduct(connection, bond.proposerId(), bond.cost(), GOLD_REASON)) {
            throw new Refused(DaoLuOutcome.INSUFFICIENT_GOLD);
        }
        JdbcOperations.update(
                connection,
                AUDIT,
                bond.proposerId(),
                "DAO_LU_BOUND",
                "{\"partnerId\":" + bond.accepterId() + ",\"goldCost\":" + bond.cost() + "}",
                Timestamp.from(Instant.now()));
        return DaoLuOutcome.OK;
    }

    @Override
    public Optional<Long> dissolve(long characterId) {
        return JdbcOperations.query(
                        dataSource,
                        DELETE_OF,
                        rs -> rs.getLong(1) == characterId ? rs.getLong(2) : rs.getLong(1),
                        characterId,
                        characterId)
                .stream()
                .findFirst();
    }

    @Override
    public int intimacyBetween(long firstId, long secondId) {
        return JdbcOperations.queryOne(dataSource, INTIMACY, rs -> rs.getInt(1), firstId, secondId, secondId, firstId)
                .orElse(0);
    }

    /** Từ chối giữa giao dịch — rollback mọi thứ đã ghi. */
    private static final class Refused extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient DaoLuOutcome outcome;

        Refused(DaoLuOutcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }
}
