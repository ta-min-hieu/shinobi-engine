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

import javax.sql.DataSource;

import dev.kage.checkin.domain.WeeklyTaskPort;
import dev.kage.server.database.GoldCapExceededException;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;

/**
 * Hiện thực {@link WeeklyTaskPort} (ADR-154, V125). Đếm = một UPSERT cộng dồn (an toàn khi nhiều lượt hạ quái chạy song
 * song trên Virtual Thread); nhận thưởng = một giao dịch mở đầu bằng khoá túi, khoá dòng tiến độ ({@code FOR UPDATE}).
 */
public final class PostgresWeeklyTaskAdapter implements WeeklyTaskPort {

    private static final String REASON = "gold.reason.weekly_task";

    private static final String TASKS = """
            SELECT t.code, t.name_key, t.target, COALESCE(c.progress, 0), c.claimed_at IS NOT NULL,
                   t.reward_gold, COALESCE(t.reward_item_template_id, 0), t.reward_quantity
              FROM weekly_task t
              LEFT JOIN character_weekly_task c
                ON c.task_code = t.code AND c.character_id = ? AND c.week_start = ?
             ORDER BY t.sort_order
            """;

    private static final String RECORD = """
            INSERT INTO character_weekly_task (character_id, week_start, task_code, progress)
            SELECT ?, ?, t.code, 1 FROM weekly_task t WHERE t.activity_code = ?
            ON CONFLICT (character_id, week_start, task_code)
            DO UPDATE SET progress = character_weekly_task.progress + 1
            """;

    private static final String LOCK_ROW = """
            SELECT progress, claimed_at IS NOT NULL FROM character_weekly_task
             WHERE character_id = ? AND week_start = ? AND task_code = ?
               FOR UPDATE
            """;

    private static final String MARK = """
            UPDATE character_weekly_task SET claimed_at = ?
             WHERE character_id = ? AND week_start = ? AND task_code = ?
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Một lệnh nhận thưởng. */
    private record Claim(long characterId, LocalDate weekStart, String taskCode, Instant now) {}

    private final DataSource dataSource;

    public PostgresWeeklyTaskAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Task> tasks(long characterId, LocalDate weekStart) {
        return JdbcOperations.query(
                dataSource, TASKS, PostgresWeeklyTaskAdapter::task, characterId, Date.valueOf(weekStart));
    }

    @Override
    public void record(long characterId, LocalDate weekStart, String activityCode) {
        JdbcOperations.update(dataSource, RECORD, characterId, Date.valueOf(weekStart), activityCode);
    }

    @Override
    public Outcome claim(long characterId, LocalDate weekStart, String taskCode, Instant now) {
        try {
            return JdbcTransactionRunner.call(
                    dataSource, connection -> claimIn(connection, new Claim(characterId, weekStart, taskCode, now)));
        } catch (Abort abort) {
            return abort.outcome;
        } catch (GoldCapExceededException capped) {
            return Outcome.GOLD_CAP;
        }
    }

    private static Outcome claimIn(Connection connection, Claim claim) {
        long characterId = claim.characterId();
        Date week = Date.valueOf(claim.weekStart());
        CharacterInventoryLock.acquire(connection, characterId);
        Task task = JdbcOperations.query(connection, TASKS, PostgresWeeklyTaskAdapter::task, characterId, week).stream()
                .filter(t -> t.code().equals(claim.taskCode()))
                .findFirst()
                .orElseThrow(() -> new Abort(Outcome.UNKNOWN_TASK));
        // {đã đủ chỉ tiêu, đã nhận}
        boolean[] state = JdbcOperations.queryOne(
                        connection,
                        LOCK_ROW,
                        rs -> new boolean[] {rs.getInt(1) >= task.target(), rs.getBoolean(2)},
                        characterId,
                        week,
                        claim.taskCode())
                .orElseThrow(() -> new Abort(Outcome.NOT_DONE));
        if (state[1]) {
            throw new Abort(Outcome.ALREADY_CLAIMED);
        }
        if (!state[0]) {
            throw new Abort(Outcome.NOT_DONE);
        }
        grant(connection, characterId, task);
        Timestamp at = Timestamp.from(claim.now());
        JdbcOperations.update(connection, MARK, at, characterId, week, claim.taskCode());
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "WEEKLY_TASK_CLAIM",
                "{\"task\":\"" + task.code() + "\",\"week\":\"" + claim.weekStart() + "\",\"gold\":" + task.gold()
                        + ",\"itemTemplateId\":" + task.itemTemplateId() + ",\"quantity\":" + task.quantity() + "}",
                at);
        return Outcome.OK;
    }

    private static void grant(Connection connection, long characterId, Task task) {
        if (task.itemTemplateId() != 0
                && !InventoryStackMerge.mergeIntoExistingStack(
                        connection, characterId, task.itemTemplateId(), task.quantity())
                && !InventoryStackMerge.insertRespectingMaxStack(
                        connection, characterId, task.itemTemplateId(), task.quantity())) {
            throw new Abort(Outcome.BAG_FULL);
        }
        if (task.gold() > 0) {
            GoldLedger.credit(connection, characterId, task.gold(), REASON);
        }
    }

    private static Task task(ResultSet rs) throws SQLException {
        return new Task(
                rs.getString(1),
                rs.getString(2),
                rs.getInt(3),
                rs.getInt(4),
                rs.getBoolean(5),
                rs.getLong(6),
                rs.getLong(7),
                rs.getInt(8));
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
