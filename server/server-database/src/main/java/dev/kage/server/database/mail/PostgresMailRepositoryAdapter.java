package dev.kage.server.database.mail;

import dev.kage.mail.domain.Mail;
import dev.kage.mail.domain.MailAttachment;
import dev.kage.mail.domain.MailRepositoryPort;
import dev.kage.mail.domain.NewMail;
import dev.kage.mail.domain.Transaction;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Hiện thực {@link MailRepositoryPort} trên PostgreSQL — bảng {@code mail}/{@code mail_item} (V13).
 * SQL chỉ tồn tại ở đây (Điều 24). {@code sender_id} dùng {@code setNull}/{@code getObject} vì
 * {@code Long} nullable (thư hệ thống, xem javadoc {@link dev.kage.mail.domain.Mail}).
 */
public final class PostgresMailRepositoryAdapter implements MailRepositoryPort {

    private static final String INSERT_MAIL = """
            INSERT INTO mail (sender_id, recipient_id, subject, body, gold_amount, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String INSERT_MAIL_ITEM = """
            INSERT INTO mail_item (mail_id, item_template_id, quantity) VALUES (?, ?, ?)
            """;

    private static final String FIND_BY_ID = """
            SELECT id, sender_id, recipient_id, subject, body, gold_amount, claimed_at, created_at
              FROM mail WHERE id = ?
            """;

    private static final String FIND_BY_ID_FOR_UPDATE = FIND_BY_ID + " FOR UPDATE";

    private static final String LIST_BY_RECIPIENT = """
            SELECT id, sender_id, recipient_id, subject, body, gold_amount, claimed_at, created_at
              FROM mail WHERE recipient_id = ? ORDER BY created_at DESC
            """;

    private static final String FIND_ATTACHMENTS_BY_MAIL = """
            SELECT item_template_id, quantity FROM mail_item WHERE mail_id = ?
            """;

    private static final String MARK_CLAIMED = """
            UPDATE mail SET claimed_at = ? WHERE id = ? AND recipient_id = ? AND claimed_at IS NULL
            """;

    private static final String DELETE_MAIL_ITEMS = """
            DELETE FROM mail_item WHERE mail_id = ?
            """;

    private static final String DELETE_MAIL = """
            DELETE FROM mail WHERE id = ? AND recipient_id = ?
            """;

    private final DataSource dataSource;

    public PostgresMailRepositoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Mail send(Transaction tx, NewMail newMail) {
        var connection = ((JdbcTransaction) tx).connection();
        try {
            long mailId = insertMail(connection, newMail);
            for (var attachment : newMail.attachments()) {
                insertMailItem(connection, mailId, attachment);
            }
            return new Mail(
                    mailId, newMail.senderId(), newMail.recipientId(), newMail.subject(), newMail.body(),
                    newMail.goldAmount(), newMail.attachments(), null, Instant.now());
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi gửi thư: recipientId=" + newMail.recipientId(), e);
        }
    }

    private static long insertMail(Connection connection, NewMail newMail) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_MAIL)) {
            if (newMail.senderId() == null) {
                statement.setNull(1, Types.BIGINT);
            } else {
                statement.setLong(1, newMail.senderId());
            }
            statement.setLong(2, newMail.recipientId());
            statement.setString(3, newMail.subject());
            statement.setString(4, newMail.body());
            statement.setLong(5, newMail.goldAmount());
            statement.setTimestamp(6, Timestamp.from(Instant.now()));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static void insertMailItem(Connection connection, long mailId, MailAttachment attachment)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_MAIL_ITEM)) {
            statement.setLong(1, mailId);
            statement.setLong(2, attachment.itemTemplateId());
            statement.setInt(3, attachment.quantity());
            statement.executeUpdate();
        }
    }

    @Override
    public Optional<Mail> findById(long mailId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_BY_ID)) {
            statement.setLong(1, mailId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(connection, rs));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đọc thư: mailId=" + mailId, e);
        }
    }

    @Override
    public Optional<Mail> findByIdForUpdate(Transaction tx, long mailId) {
        var connection = ((JdbcTransaction) tx).connection();
        try (var statement = connection.prepareStatement(FIND_BY_ID_FOR_UPDATE)) {
            statement.setLong(1, mailId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(connection, rs));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi khoá dòng thư: mailId=" + mailId, e);
        }
    }

    @Override
    public List<Mail> listForRecipient(long recipientId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(LIST_BY_RECIPIENT)) {
            statement.setLong(1, recipientId);
            try (ResultSet rs = statement.executeQuery()) {
                var result = new ArrayList<Mail>();
                while (rs.next()) {
                    result.add(mapRow(connection, rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi liệt kê thư: recipientId=" + recipientId, e);
        }
    }

    @Override
    public boolean markClaimed(Transaction tx, long mailId, long recipientId) {
        var connection = ((JdbcTransaction) tx).connection();
        try (var statement = connection.prepareStatement(MARK_CLAIMED)) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setLong(2, mailId);
            statement.setLong(3, recipientId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đánh dấu đã nhận thư: mailId=" + mailId, e);
        }
    }

    @Override
    public boolean delete(long mailId, long recipientId) {
        try (var connection = dataSource.getConnection()) {
            try (var deleteItems = connection.prepareStatement(DELETE_MAIL_ITEMS)) {
                deleteItems.setLong(1, mailId);
                deleteItems.executeUpdate();
            }
            try (var deleteMail = connection.prepareStatement(DELETE_MAIL)) {
                deleteMail.setLong(1, mailId);
                deleteMail.setLong(2, recipientId);
                return deleteMail.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi xoá thư: mailId=" + mailId, e);
        }
    }

    private static Mail mapRow(Connection connection, ResultSet rs) throws SQLException {
        long mailId = rs.getLong("id");
        Long senderIdObj = rs.getObject("sender_id", Long.class);
        var claimedAtTs = rs.getTimestamp("claimed_at");
        return new Mail(
                mailId,
                senderIdObj,
                rs.getLong("recipient_id"),
                rs.getString("subject"),
                rs.getString("body"),
                rs.getLong("gold_amount"),
                findAttachments(connection, mailId),
                claimedAtTs == null ? null : claimedAtTs.toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }

    private static List<MailAttachment> findAttachments(Connection connection, long mailId) throws SQLException {
        try (var statement = connection.prepareStatement(FIND_ATTACHMENTS_BY_MAIL)) {
            statement.setLong(1, mailId);
            try (ResultSet rs = statement.executeQuery()) {
                var result = new ArrayList<MailAttachment>();
                while (rs.next()) {
                    result.add(new MailAttachment(rs.getLong("item_template_id"), rs.getInt("quantity")));
                }
                return result;
            }
        }
    }
}
