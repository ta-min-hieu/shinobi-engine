package dev.kage.server.database.character;

import java.io.Serial;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import javax.sql.DataSource;

import dev.kage.character.domain.MilestonePort;
import dev.kage.server.database.GoldCapExceededException;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;

/**
 * Hiện thực {@link MilestonePort} (ADR-153, V124). Nhận quà mở đầu bằng khoá túi của nhân vật (thứ tự khoá chung,
 * DATABASE.md §5.1); hai lệnh nhận song song chạm PRIMARY KEY nên không thể nhận hai lần.
 */
public final class PostgresMilestoneAdapter implements MilestonePort {

    private static final String REASON = "gold.reason.milestone";

    private static final String LEVEL = "SELECT level FROM character WHERE id = ?";

    private static final String BOARD = """
            SELECT m.level, m.name_key, m.reward_gold, COALESCE(m.reward_item_template_id, 0), m.reward_quantity,
                   EXISTS (SELECT 1 FROM character_milestone_claim c WHERE c.character_id = ? AND c.level = m.level)
              FROM realm_milestone m
             ORDER BY m.level
            """;

    private static final String CLAIM = """
            INSERT INTO character_milestone_claim (character_id, level, claimed_at) VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresMilestoneAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Board board(long characterId) {
        return JdbcTransactionRunner.call(
                dataSource,
                connection -> new Board(
                        JdbcOperations.queryOne(connection, LEVEL, rs -> rs.getInt(1), characterId)
                                .orElse(1),
                        JdbcOperations.query(connection, BOARD, PostgresMilestoneAdapter::milestone, characterId)));
    }

    @Override
    public Outcome claim(long characterId, int level, Instant now) {
        try {
            return JdbcTransactionRunner.call(dataSource, connection -> claimIn(connection, characterId, level, now));
        } catch (Abort abort) {
            return abort.outcome;
        } catch (GoldCapExceededException capped) {
            return Outcome.GOLD_CAP;
        }
    }

    private static Outcome claimIn(Connection connection, long characterId, int level, Instant now) {
        CharacterInventoryLock.acquire(connection, characterId);
        int characterLevel = JdbcOperations.queryOne(connection, LEVEL, rs -> rs.getInt(1), characterId)
                .orElse(0);
        Milestone milestone =
                JdbcOperations.query(connection, BOARD, PostgresMilestoneAdapter::milestone, characterId).stream()
                        .filter(m -> m.level() == level)
                        .findFirst()
                        .orElseThrow(() -> new Abort(Outcome.UNKNOWN_MILESTONE));
        if (characterLevel < level) {
            throw new Abort(Outcome.LEVEL_TOO_LOW);
        }
        if (JdbcOperations.update(connection, CLAIM, characterId, level, Timestamp.from(now)) == 0) {
            throw new Abort(Outcome.ALREADY_CLAIMED);
        }
        grant(connection, characterId, milestone);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "MILESTONE_CLAIM",
                "{\"level\":" + level + ",\"gold\":" + milestone.gold() + ",\"itemTemplateId\":"
                        + milestone.itemTemplateId() + ",\"quantity\":" + milestone.quantity() + "}",
                Timestamp.from(now));
        return Outcome.OK;
    }

    /** Trao quà — túi đầy ⇒ Abort; vượt trần vàng ⇒ {@code GoldCapExceededException} (rollback trọn). */
    private static void grant(Connection connection, long characterId, Milestone milestone) {
        if (milestone.itemTemplateId() != 0
                && !InventoryStackMerge.mergeIntoExistingStack(
                        connection, characterId, milestone.itemTemplateId(), milestone.quantity())
                && !InventoryStackMerge.insertRespectingMaxStack(
                        connection, characterId, milestone.itemTemplateId(), milestone.quantity())) {
            throw new Abort(Outcome.BAG_FULL);
        }
        if (milestone.gold() > 0) {
            GoldLedger.credit(connection, characterId, milestone.gold(), REASON);
        }
    }

    private static Milestone milestone(ResultSet rs) throws SQLException {
        return new Milestone(
                rs.getInt(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getInt(5), rs.getBoolean(6));
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
