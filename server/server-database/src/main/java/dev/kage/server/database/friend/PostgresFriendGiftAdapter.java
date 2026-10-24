package dev.kage.server.database.friend;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.friend.domain.FriendGiftOutcome;
import dev.kage.friend.domain.FriendGiftPolicy;
import dev.kage.friend.domain.FriendGiftPort;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link FriendGiftPort} (ADR-084, V80). Thứ tự trong giao dịch: khoá dòng {@code friendship} → ghi nhật ký
 * quà
 * trong ngày (PK chặn lần hai) → trừ vàng qua {@link GoldLedger} (phát {@code GoldChangedEvent}) → cộng thân mật →
 * audit.
 */
public final class PostgresFriendGiftAdapter implements FriendGiftPort {

    private static final String GOLD_REASON = "gold.reason.friend_gift";

    private static final String LOCK_FRIENDSHIP = """
            SELECT id, intimacy FROM friendship
             WHERE status = 'ACCEPTED'
               AND ((requester_id = ? AND addressee_id = ?) OR (requester_id = ? AND addressee_id = ?))
               FOR UPDATE
            """;

    private static final String LOG_GIFT = """
            INSERT INTO friend_gift_log (sender_id, recipient_id, gift_date) VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

    private static final String ADD_INTIMACY = "UPDATE friendship SET intimacy = intimacy + ? WHERE id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private static final String INTIMACY_OF = """
            SELECT CASE WHEN requester_id = ? THEN addressee_id ELSE requester_id END, intimacy
              FROM friendship
             WHERE status = 'ACCEPTED' AND (requester_id = ? OR addressee_id = ?)
            """;

    private record Pair(long friendshipId, int intimacy) {}

    private final DataSource dataSource;

    public PostgresFriendGiftAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public GiftResult gift(long senderId, long recipientId, LocalDate today) {
        try {
            return JdbcTransactionRunner.call(
                    dataSource, connection -> giftIn(connection, senderId, recipientId, today));
        } catch (Refused refused) {
            return GiftResult.refused(refused.outcome);
        }
    }

    private static GiftResult giftIn(Connection connection, long senderId, long recipientId, LocalDate today) {
        Optional<Pair> pair = JdbcOperations.queryOne(
                connection,
                LOCK_FRIENDSHIP,
                rs -> new Pair(rs.getLong(1), rs.getInt(2)),
                senderId,
                recipientId,
                recipientId,
                senderId);
        if (pair.isEmpty()) {
            return GiftResult.refused(FriendGiftOutcome.NOT_FRIENDS);
        }
        if (JdbcOperations.update(connection, LOG_GIFT, senderId, recipientId, Date.valueOf(today)) == 0) {
            return GiftResult.refused(FriendGiftOutcome.ALREADY_GIFTED_TODAY);
        }
        if (!GoldLedger.deduct(connection, senderId, FriendGiftPolicy.GIFT_COST, GOLD_REASON)) {
            throw new Refused(FriendGiftOutcome.INSUFFICIENT_GOLD); // rollback dòng nhật ký vừa ghi
        }
        JdbcOperations.update(
                connection,
                ADD_INTIMACY,
                FriendGiftPolicy.INTIMACY_PER_GIFT,
                pair.get().friendshipId());
        int after = pair.get().intimacy() + FriendGiftPolicy.INTIMACY_PER_GIFT;
        JdbcOperations.update(
                connection,
                AUDIT,
                senderId,
                "FRIEND_GIFT",
                "{\"recipientId\":" + recipientId + ",\"goldCost\":" + FriendGiftPolicy.GIFT_COST + ",\"intimacy\":"
                        + after + "}",
                Timestamp.from(Instant.now()));
        return new GiftResult(FriendGiftOutcome.OK, pair.get().intimacy(), after);
    }

    @Override
    public Map<Long, Integer> intimacyOf(long characterId) {
        Map<Long, Integer> intimacy = new HashMap<>();
        JdbcOperations.query(
                        dataSource,
                        INTIMACY_OF,
                        rs -> Map.entry(rs.getLong(1), rs.getInt(2)),
                        characterId,
                        characterId,
                        characterId)
                .forEach(row -> intimacy.put(row.getKey(), row.getValue()));
        return intimacy;
    }

    /** Từ chối giữa giao dịch — rollback mọi thứ đã ghi. */
    private static final class Refused extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient FriendGiftOutcome outcome;

        Refused(FriendGiftOutcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }
}
