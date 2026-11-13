package dev.kage.server.database.mail;

import java.sql.Connection;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import dev.kage.mail.domain.MailHousekeepingPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link MailHousekeepingPort} (ADR-216/217, V164). "Đã xong" = cùng luật xoá từng thư ({@code
 * DeleteMailUseCase}): đã nhận ({@code claimed_at}), hoặc không vàng không vật phẩm đính kèm — thư còn quà chưa nhận
 * không bao giờ bị xoá.
 */
public final class PostgresMailHousekeepingAdapter implements MailHousekeepingPort {

    private static final String FINISHED = """
            SELECT m.id FROM mail m
             WHERE m.recipient_id = ? AND NOT m.pinned AND m.archived_at IS NULL
               AND (m.claimed_at IS NOT NULL
                    OR (m.gold_amount = 0 AND NOT EXISTS (SELECT 1 FROM mail_item mi WHERE mi.mail_id = m.id)))
             FOR UPDATE OF m
            """;

    private static final String DELETE_ITEMS = "DELETE FROM mail_item WHERE mail_id = ?";
    private static final String DELETE_MAIL = "DELETE FROM mail WHERE id = ? AND recipient_id = ?";
    private static final String SET_PINNED = "UPDATE mail SET pinned = ? WHERE id = ? AND recipient_id = ?";
    private static final String PINNED = "SELECT id FROM mail WHERE recipient_id = ? AND pinned";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresMailHousekeepingAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int deleteFinished(long recipientId) {
        return JdbcTransactionRunner.call(dataSource, connection -> deleteIn(connection, recipientId));
    }

    private static int deleteIn(Connection connection, long recipientId) {
        int deleted = 0;
        for (long mailId : JdbcOperations.query(connection, FINISHED, rs -> rs.getLong(1), recipientId)) {
            JdbcOperations.update(connection, DELETE_ITEMS, mailId);
            deleted += JdbcOperations.update(connection, DELETE_MAIL, mailId, recipientId);
        }
        return deleted;
    }

    @Override
    public boolean setPinned(long recipientId, long mailId, boolean pinned) {
        return JdbcOperations.update(dataSource, SET_PINNED, pinned, mailId, recipientId) == 1;
    }

    @Override
    public Set<Long> pinned(long recipientId) {
        return new HashSet<>(JdbcOperations.query(dataSource, PINNED, rs -> rs.getLong(1), recipientId));
    }
}
