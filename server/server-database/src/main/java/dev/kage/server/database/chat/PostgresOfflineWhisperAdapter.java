package dev.kage.server.database.chat;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.chat.domain.OfflineWhisperPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@link OfflineWhisperPort} (ADR-187, V148). */
public final class PostgresOfflineWhisperAdapter implements OfflineWhisperPort {

    // Khoá dòng nhân vật người nhận để "đếm rồi ghi" của hai người gửi đồng thời không vượt trần.
    private static final String LOCK_RECIPIENT = "SELECT id FROM character WHERE id = ? FOR UPDATE";
    private static final String COUNT = "SELECT COUNT(*) FROM offline_whisper WHERE recipient_id = ?";
    private static final String INSERT = """
            INSERT INTO offline_whisper (recipient_id, sender_id, body, created_at) VALUES (?, ?, ?, ?)
            """;

    private static final String TAKE = """
            DELETE FROM offline_whisper w USING character c
             WHERE w.recipient_id = ? AND c.id = w.sender_id
            RETURNING w.id, w.sender_id, c.name, w.body, w.created_at
            """;

    private final DataSource dataSource;

    public PostgresOfflineWhisperAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Một tin vừa lấy ra (kèm id để xếp đúng thứ tự gửi). */
    private record Row(long id, Pending pending) {}

    @Override
    public boolean store(long senderId, long recipientId, String body, Instant at, int maxPending) {
        return JdbcTransactionRunner.call(
                dataSource,
                connection -> storeIn(connection, new Message(senderId, recipientId, body, at), maxPending));
    }

    /** Một tin cần giữ. */
    private record Message(long senderId, long recipientId, String body, Instant at) {}

    private static boolean storeIn(Connection connection, Message message, int maxPending) {
        JdbcOperations.queryOne(connection, LOCK_RECIPIENT, rs -> rs.getLong(1), message.recipientId());
        int pending = JdbcOperations.queryOne(connection, COUNT, rs -> rs.getInt(1), message.recipientId())
                .orElse(0);
        if (pending >= maxPending) {
            return false;
        }
        JdbcOperations.update(
                connection,
                INSERT,
                message.recipientId(),
                message.senderId(),
                message.body(),
                Timestamp.from(message.at()));
        return true;
    }

    @Override
    public List<Pending> takeAll(long recipientId, Instant since) {
        return JdbcOperations.query(
                        dataSource,
                        TAKE,
                        rs -> new Row(
                                rs.getLong(1),
                                new Pending(
                                        rs.getLong(2),
                                        rs.getString(3),
                                        rs.getString(4),
                                        rs.getTimestamp(5).toInstant())),
                        recipientId)
                .stream()
                .filter(row -> !row.pending().sentAt().isBefore(since))
                .sorted(java.util.Comparator.comparingLong(Row::id))
                .map(Row::pending)
                .toList();
    }
}
