package dev.kage.server.database.friend;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.friend.domain.SwornOutcome;
import dev.kage.friend.domain.SwornPolicy;
import dev.kage.friend.domain.SwornPort;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link SwornPort} (ADR-110, V90). Nhận lời: khoá dòng {@code friendship} của cặp (cùng thứ tự khoá đạo
 * lữ) → khoá nhóm của người mời → kiểm sĩ số → chèn thành viên (khoá chính {@code character_id} chặn người đã ở nhóm
 * khác) → trừ vàng qua {@link GoldLedger} → audit. Từ chối giữa chừng ném {@link Refused} để rollback.
 */
public final class PostgresSwornAdapter implements SwornPort {

    private static final String GOLD_REASON = "gold.reason.sworn";

    private static final String MEMBERS_OF = """
            SELECT m.character_id FROM sworn_member m
             WHERE m.group_id = (SELECT group_id FROM sworn_member WHERE character_id = ?)
             ORDER BY m.joined_at, m.character_id
            """;

    private static final String INTIMACY = """
            SELECT intimacy FROM friendship
             WHERE status = 'ACCEPTED'
               AND ((requester_id = ? AND addressee_id = ?) OR (requester_id = ? AND addressee_id = ?))
            """;

    private static final String LOCK_FRIENDSHIP = INTIMACY + " FOR UPDATE";

    private static final String GROUP_OF = "SELECT group_id FROM sworn_member WHERE character_id = ?";

    private static final String LOCK_GROUP = "SELECT id FROM sworn_group WHERE id = ? FOR UPDATE";

    private static final String COUNT_GROUP = "SELECT COUNT(*) FROM sworn_member WHERE group_id = ?";

    private static final String NEW_GROUP = "INSERT INTO sworn_group (created_at) VALUES (?) RETURNING id";

    private static final String ADD_MEMBER = """
            INSERT INTO sworn_member (character_id, group_id, joined_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING
            """;

    private static final String REMOVE_MEMBER = "DELETE FROM sworn_member WHERE character_id = ? RETURNING group_id";

    private static final String REMAINING = "SELECT character_id FROM sworn_member WHERE group_id = ?";

    private static final String DELETE_GROUP = "DELETE FROM sworn_group WHERE id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresSwornAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Long> membersOf(long characterId) {
        return JdbcOperations.query(dataSource, MEMBERS_OF, rs -> rs.getLong(1), characterId);
    }

    @Override
    public SwornOutcome join(long inviterId, long inviteeId) {
        try {
            return JdbcTransactionRunner.call(dataSource, connection -> joinIn(connection, inviterId, inviteeId));
        } catch (Refused refused) {
            return refused.outcome;
        }
    }

    private static SwornOutcome joinIn(Connection connection, long inviterId, long inviteeId) {
        int intimacy = JdbcOperations.queryOne(
                        connection, LOCK_FRIENDSHIP, rs -> rs.getInt(1), inviterId, inviteeId, inviteeId, inviterId)
                .orElse(0);
        if (intimacy < SwornPolicy.MIN_INTIMACY) {
            return SwornOutcome.INTIMACY_TOO_LOW;
        }
        Instant now = Instant.now();
        Optional<Long> existing = JdbcOperations.queryOne(connection, GROUP_OF, rs -> rs.getLong(1), inviterId);
        long groupId;
        long cost;
        if (existing.isPresent()) {
            groupId = existing.get();
            JdbcOperations.lock(connection, LOCK_GROUP, groupId);
            long size = JdbcOperations.queryOne(connection, COUNT_GROUP, rs -> rs.getLong(1), groupId)
                    .orElse(0L);
            if (size >= SwornPolicy.MAX_MEMBERS) {
                return SwornOutcome.GROUP_FULL;
            }
            cost = SwornPolicy.JOIN_COST;
        } else {
            groupId = JdbcOperations.insertReturningId(connection, NEW_GROUP, Timestamp.from(now));
            JdbcOperations.update(connection, ADD_MEMBER, inviterId, groupId, Timestamp.from(now));
            cost = SwornPolicy.FOUND_COST;
        }
        if (JdbcOperations.update(connection, ADD_MEMBER, inviteeId, groupId, Timestamp.from(now)) == 0) {
            throw new Refused(SwornOutcome.ALREADY_SWORN); // rollback nhóm vừa lập (nếu có)
        }
        if (!GoldLedger.deduct(connection, inviterId, cost, GOLD_REASON)) {
            throw new Refused(SwornOutcome.INSUFFICIENT_GOLD);
        }
        JdbcOperations.update(
                connection,
                AUDIT,
                inviterId,
                "SWORN_JOINED",
                "{\"groupId\":" + groupId + ",\"inviteeId\":" + inviteeId + ",\"goldCost\":" + cost + "}",
                Timestamp.from(now));
        return SwornOutcome.OK;
    }

    @Override
    public List<Long> leave(long characterId) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            Optional<Long> group = JdbcOperations.queryOne(connection, REMOVE_MEMBER, rs -> rs.getLong(1), characterId);
            if (group.isEmpty()) {
                return List.<Long>of();
            }
            List<Long> remaining = JdbcOperations.query(connection, REMAINING, rs -> rs.getLong(1), group.get());
            if (remaining.size() < 2) {
                JdbcOperations.update(connection, DELETE_GROUP, group.get()); // CASCADE xoá người cuối
            }
            return remaining;
        });
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

        private final transient SwornOutcome outcome;

        Refused(SwornOutcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }
}
