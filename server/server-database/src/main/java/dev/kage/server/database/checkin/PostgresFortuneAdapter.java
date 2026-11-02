package dev.kage.server.database.checkin;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

import javax.sql.DataSource;

import dev.kage.checkin.domain.FortunePort;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;

/**
 * Hiện thực {@link FortunePort} (ADR-149, V121). Gieo là MỘT giao dịch mở đầu bằng khoá túi của nhân vật (thứ tự khoá
 * chung, DATABASE.md §5.1) — hai lệnh gieo song song không thể cùng qua bước "chưa gieo hôm nay".
 */
public final class PostgresFortuneAdapter implements FortunePort {

    private static final String REASON = "gold.reason.fortune";

    private static final String COLUMNS = """
            f.code, f.name_key, f.text_key, f.exp_percent, f.reward_gold,
            COALESCE(f.reward_item_template_id, 0), f.reward_quantity
            """;

    private static final String TODAY = "SELECT " + COLUMNS + """
              FROM character_fortune cf
              JOIN fortune f ON f.id = cf.fortune_id
             WHERE cf.character_id = ? AND cf.day = ?
            """;

    private static final String ALL = "SELECT f.id, f.weight, " + COLUMNS + " FROM fortune f ORDER BY f.id";

    private static final String RECORD = """
            INSERT INTO character_fortune (character_id, day, fortune_id, drawn_at) VALUES (?, ?, ?, ?)
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Một dòng quẻ để chọn theo trọng số. */
    private record Row(long id, int weight, Fortune fortune) {}

    /** Một lệnh gieo. */
    private record Request(long characterId, LocalDate day, Instant now, ToIntFunction<int[]> pick) {}

    private final DataSource dataSource;

    public PostgresFortuneAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Fortune> today(long characterId, LocalDate day) {
        return JdbcOperations.queryOne(dataSource, TODAY, rs -> fortune(rs, 1), characterId, Date.valueOf(day));
    }

    @Override
    public Draw draw(long characterId, LocalDate day, Instant now, ToIntFunction<int[]> pick) {
        try {
            return JdbcTransactionRunner.call(
                    dataSource, connection -> drawIn(connection, new Request(characterId, day, now, pick)));
        } catch (Abort abort) {
            return new Draw(abort.outcome, Optional.empty());
        }
    }

    private static Draw drawIn(Connection connection, Request request) {
        long characterId = request.characterId();
        CharacterInventoryLock.acquire(connection, characterId);
        Optional<Fortune> drawn = JdbcOperations.queryOne(
                connection, TODAY, rs -> fortune(rs, 1), characterId, Date.valueOf(request.day()));
        if (drawn.isPresent()) {
            return new Draw(Outcome.ALREADY_DRAWN, drawn);
        }
        List<Row> rows =
                JdbcOperations.query(connection, ALL, rs -> new Row(rs.getLong(1), rs.getInt(2), fortune(rs, 3)));
        Row chosen = rows.get(
                request.pick().applyAsInt(rows.stream().mapToInt(Row::weight).toArray()));
        Fortune fortune = chosen.fortune();
        if (fortune.itemTemplateId() != 0
                && !InventoryStackMerge.mergeIntoExistingStack(
                        connection, characterId, fortune.itemTemplateId(), fortune.quantity())
                && !InventoryStackMerge.insertRespectingMaxStack(
                        connection, characterId, fortune.itemTemplateId(), fortune.quantity())) {
            throw new Abort(Outcome.BAG_FULL);
        }
        if (fortune.gold() > 0) {
            GoldLedger.credit(connection, characterId, fortune.gold(), REASON);
        }
        Timestamp at = Timestamp.from(request.now());
        JdbcOperations.update(connection, RECORD, characterId, Date.valueOf(request.day()), chosen.id(), at);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "FORTUNE_DRAW",
                "{\"fortune\":\"" + fortune.code() + "\",\"gold\":" + fortune.gold() + ",\"itemTemplateId\":"
                        + fortune.itemTemplateId() + ",\"quantity\":" + fortune.quantity() + "}",
                at);
        return new Draw(Outcome.OK, Optional.of(fortune));
    }

    /** Đọc một quẻ bắt đầu từ cột {@code first} (theo thứ tự {@link #COLUMNS}). */
    private static Fortune fortune(ResultSet rs, int first) throws SQLException {
        return new Fortune(
                rs.getString(first),
                rs.getString(first + 1),
                rs.getString(first + 2),
                rs.getInt(first + 3),
                rs.getLong(first + 4),
                rs.getLong(first + 5),
                rs.getInt(first + 6));
    }

    /** Từ chối giữa transaction — ném để rollback. */
    private static final class Abort extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Outcome outcome;

        Abort(Outcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }
}
