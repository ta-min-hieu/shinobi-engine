package dev.kage.server.database.mail;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import dev.kage.mail.domain.Mail;
import dev.kage.mail.domain.MailAttachment;
import dev.kage.mail.domain.MailRepositoryPort;
import dev.kage.mail.domain.NewMail;
import dev.kage.mail.domain.Transaction;
import dev.kage.server.database.DatabaseAccessException;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link MailRepositoryPort} trên PostgreSQL — bảng {@code mail}/{@code mail_item} (V13).
 * SQL chỉ tồn tại ở đây (Điều 24). {@code sender_id} là {@code Long} nullable (thư hệ thống, xem
 * javadoc {@link dev.kage.mail.domain.Mail}) — {@link JdbcOperations} bind {@code null} qua
 * {@code setObject} chung, đã verify bằng {@code MailIntegrationTest} (thư hệ thống sender null).
 *
 * <p>{@link #findById}, {@link #findByIdForUpdate}, {@link #listForRecipient} giữ nguyên vòng lặp
 * {@link ResultSet} thủ công (không qua {@code JdbcOperations.query}) vì {@link #mapRow} cần
 * {@link Connection} để truy vấn lồng {@link #findAttachments} — không khớp chữ ký
 * {@code RowMapper<T>} (chỉ nhận {@link ResultSet}).
 */
public final class PostgresMailRepositoryAdapter implements MailRepositoryPort {

    private static final String INSERT_MAIL = """
            INSERT INTO mail (sender_id, recipient_id, subject, body, gold_amount, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String INSERT_MAIL_ITEM = """
            INSERT INTO mail_item (mail_id, item_template_id, quantity, enhance_level) VALUES (?, ?, ?, ?)
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
            SELECT item_template_id, quantity, enhance_level FROM mail_item WHERE mail_id = ?
            """;

    private static final String MARK_CLAIMED = """
            UPDATE mail SET claimed_at = ? WHERE id = ? AND recipient_id = ? AND claimed_at IS NULL
            """;

    // Ràng buộc người nhận NGAY TẠI ĐÂY, không chỉ dựa vào use case (đọc ngoài transaction): người
    // nhận sai ⇒ 0 dòng, vật phẩm đính kèm của thư người khác không bị đụng.
    private static final String DELETE_MAIL_ITEMS = """
            DELETE FROM mail_item
             WHERE mail_id = (SELECT id FROM mail WHERE id = ? AND recipient_id = ? FOR UPDATE)
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
        Connection connection = ((JdbcTransaction) tx).connection();
        long mailId = insertMail(connection, newMail);
        insertMailItems(connection, mailId, newMail.attachments());
        return new Mail(
                mailId,
                newMail.senderId(),
                newMail.recipientId(),
                newMail.subject(),
                newMail.body(),
                newMail.goldAmount(),
                newMail.attachments(),
                null,
                Instant.now());
    }

    private static long insertMail(Connection connection, NewMail newMail) {
        return JdbcOperations.insertReturningId(
                connection,
                INSERT_MAIL,
                newMail.senderId(),
                newMail.recipientId(),
                newMail.subject(),
                newMail.body(),
                newMail.goldAmount(),
                Timestamp.from(Instant.now()));
    }

    private static void insertMailItems(Connection connection, long mailId, List<MailAttachment> attachments) {
        List<Object[]> batchParams = attachments.stream()
                .map(attachment -> new Object[] {
                    mailId, attachment.itemTemplateId(), attachment.quantity(), attachment.enhanceLevel()
                })
                .collect(Collectors.toList());
        JdbcOperations.batchUpdate(connection, INSERT_MAIL_ITEM, batchParams);
    }

    @Override
    public Optional<Mail> findById(long mailId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_BY_ID)) {
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
        Connection connection = ((JdbcTransaction) tx).connection();
        try (PreparedStatement statement = connection.prepareStatement(FIND_BY_ID_FOR_UPDATE)) {
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
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(LIST_BY_RECIPIENT)) {
            statement.setLong(1, recipientId);
            try (ResultSet rs = statement.executeQuery()) {
                ArrayList<Mail> result = new ArrayList<Mail>();
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
        Connection connection = ((JdbcTransaction) tx).connection();
        return JdbcOperations.update(connection, MARK_CLAIMED, Timestamp.from(Instant.now()), mailId, recipientId) > 0;
    }

    @Override
    public boolean delete(long mailId, long recipientId) {
        // Một transaction: xoá mail_item và mail cùng thành hoặc cùng bại (bản cũ autocommit từng câu
        // ⇒ hỏng giữa chừng để lại thư mất đính kèm). Cả hai câu đều ràng buộc recipient_id.
        return JdbcTransactionRunner.call(dataSource, connection -> {
            JdbcOperations.update(connection, DELETE_MAIL_ITEMS, mailId, recipientId);
            return JdbcOperations.update(connection, DELETE_MAIL, mailId, recipientId) > 0;
        });
    }

    private static Mail mapRow(Connection connection, ResultSet rs) throws SQLException {
        long mailId = rs.getLong("id");
        Long senderIdObj = rs.getObject("sender_id", Long.class);
        Timestamp claimedAtTs = rs.getTimestamp("claimed_at");
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

    private static List<MailAttachment> findAttachments(Connection connection, long mailId) {
        return JdbcOperations.query(
                connection,
                FIND_ATTACHMENTS_BY_MAIL,
                rs -> new MailAttachment(
                        rs.getLong("item_template_id"), rs.getInt("quantity"), rs.getInt("enhance_level")),
                mailId);
    }
}
