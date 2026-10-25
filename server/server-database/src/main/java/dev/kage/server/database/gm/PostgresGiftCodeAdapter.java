package dev.kage.server.database.gm;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.gm.domain.GiftCode;
import dev.kage.gm.domain.GiftCodeOutcome;
import dev.kage.gm.domain.GiftCodePort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link GiftCodePort} (ADR-104, V89). Đổi mã khoá dòng {@code gift_code} trước — hai người đổi mã cuối
 * cùng lúc thì chỉ một người lấy được lượt; khoá chính {@code gift_code_redemption} chặn một người đổi hai lần.
 */
public final class PostgresGiftCodeAdapter implements GiftCodePort {

    private static final String CREATE = """
            INSERT INTO gift_code (code, gold, item_template_id, quantity, max_uses, uses, expires_at, created_by,
                                   created_at)
            VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?) ON CONFLICT DO NOTHING
            """;

    private static final String LOCK = """
            SELECT code, gold, COALESCE(item_template_id, 0), quantity, max_uses, uses, expires_at
              FROM gift_code WHERE code = ? FOR UPDATE
            """;

    private static final String PREVIOUS = """
            SELECT mailed FROM gift_code_redemption WHERE code = ? AND character_id = ?
            """;

    private static final String INSERT_REDEMPTION = """
            INSERT INTO gift_code_redemption (code, character_id, redeemed_at) VALUES (?, ?, ?)
            """;

    private static final String COUNT_USE = "UPDATE gift_code SET uses = uses + 1 WHERE code = ?";

    private static final String MARK_MAILED =
            "UPDATE gift_code_redemption SET mailed = TRUE WHERE code = ? AND character_id = ?";

    private final DataSource dataSource;

    public PostgresGiftCodeAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean create(GiftCode code, long createdBy, Instant at) {
        return JdbcOperations.update(
                        dataSource,
                        CREATE,
                        code.code(),
                        code.gold(),
                        code.hasItem() ? code.itemTemplateId() : null,
                        code.hasItem() ? code.quantity() : 0,
                        code.maxUses(),
                        Timestamp.from(code.expiresAt()),
                        createdBy,
                        Timestamp.from(at))
                > 0;
    }

    @Override
    public Redemption redeem(String code, long characterId, Instant at) {
        return JdbcTransactionRunner.call(dataSource, connection -> redeemIn(connection, code, characterId, at));
    }

    private static Redemption redeemIn(Connection connection, String code, long characterId, Instant at) {
        Optional<GiftCode> found = JdbcOperations.queryOne(connection, LOCK, PostgresGiftCodeAdapter::map, code);
        if (found.isEmpty()) {
            return refused(GiftCodeOutcome.UNKNOWN_CODE);
        }
        GiftCode gift = found.get();
        Optional<Boolean> previous =
                JdbcOperations.queryOne(connection, PREVIOUS, rs -> rs.getBoolean(1), code, characterId);
        if (previous.isPresent()) {
            // Đã đổi: thư chưa tới thì gửi lại đúng lá thư đó (khoá chống trùng), KHÔNG tốn thêm lượt.
            return previous.get()
                    ? refused(GiftCodeOutcome.ALREADY_REDEEMED)
                    : new Redemption(GiftCodeOutcome.OK, found, true);
        }
        if (!at.isBefore(gift.expiresAt())) {
            return refused(GiftCodeOutcome.EXPIRED);
        }
        if (gift.uses() >= gift.maxUses()) {
            return refused(GiftCodeOutcome.USED_UP);
        }
        JdbcOperations.update(connection, INSERT_REDEMPTION, code, characterId, Timestamp.from(at));
        JdbcOperations.update(connection, COUNT_USE, code);
        return new Redemption(GiftCodeOutcome.OK, found, true);
    }

    private static Redemption refused(GiftCodeOutcome outcome) {
        return new Redemption(outcome, Optional.empty(), false);
    }

    @Override
    public void markMailed(String code, long characterId) {
        JdbcOperations.update(dataSource, MARK_MAILED, code, characterId);
    }

    private static GiftCode map(ResultSet rs) throws SQLException {
        return new GiftCode(
                rs.getString(1),
                rs.getLong(2),
                rs.getLong(3),
                rs.getInt(4),
                rs.getInt(5),
                rs.getInt(6),
                rs.getTimestamp(7).toInstant());
    }
}
