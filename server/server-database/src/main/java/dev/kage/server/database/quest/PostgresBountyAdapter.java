package dev.kage.server.database.quest;

import java.sql.Connection;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.quest.domain.BountyPort;
import dev.kage.quest.domain.BountyTemplate;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link BountyPort} (ADR-101, V87). Nhận: khoá dòng {@code character} → đếm đang làm / đã xong hôm nay →
 * chèn. Hạ quái: cộng tiến độ của truy nã ACTIVE khớp loài (khoá dòng đó); đủ số thì chuyển COMPLETED và cộng vàng
 * (GoldLedger) + EXP ({@code character.exp}, cùng đường thưởng quest) trong CÙNG giao dịch.
 */
public final class PostgresBountyAdapter implements BountyPort {

    private static final String GOLD_REASON = "gold.reason.bounty";

    private static final String TEMPLATES = """
            SELECT code, monster_code, kill_count, reward_gold, reward_exp, min_level FROM bounty_template ORDER BY code
            """;

    private static final String ACTIVE = """
            SELECT bounty_code, monster_code, progress, target FROM character_bounty
             WHERE character_id = ? AND status = 'ACTIVE'
            """;

    private static final String COMPLETED_ON =
            "SELECT COUNT(*) FROM character_bounty WHERE character_id = ? AND day = ? AND status = 'COMPLETED'";

    private static final String LOCK_CHARACTER = "SELECT id FROM character WHERE id = ? FOR UPDATE";

    private static final String INSERT = """
            INSERT INTO character_bounty (character_id, day, bounty_code, monster_code, target, status)
            VALUES (?, ?, ?, ?, ?, 'ACTIVE')
            """;

    private static final String ABANDON =
            "UPDATE character_bounty SET status = 'ABANDONED' WHERE character_id = ? AND status = 'ACTIVE'";

    private static final String LOCK_MATCHING = """
            SELECT cb.id, cb.bounty_code, cb.progress, cb.target, bt.reward_gold, bt.reward_exp
              FROM character_bounty cb
              JOIN bounty_template bt ON bt.code = cb.bounty_code
             WHERE cb.character_id = ? AND cb.status = 'ACTIVE' AND cb.monster_code = ?
               FOR UPDATE OF cb
            """;

    private static final String ADVANCE = "UPDATE character_bounty SET progress = ? , status = ? WHERE id = ?";

    private static final String ADD_EXP = "UPDATE character SET exp = exp + ? WHERE id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private record Row(long id, String code, int progress, int target, long gold, long exp) {}

    private final DataSource dataSource;

    public PostgresBountyAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<BountyTemplate> templates() {
        return JdbcOperations.query(
                dataSource,
                TEMPLATES,
                rs -> new BountyTemplate(
                        rs.getString(1), rs.getString(2), rs.getInt(3), rs.getLong(4), rs.getLong(5), rs.getInt(6)));
    }

    @Override
    public Optional<Active> active(long characterId) {
        return JdbcOperations.queryOne(
                dataSource,
                ACTIVE,
                rs -> new Active(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4)),
                characterId);
    }

    @Override
    public int completedOn(long characterId, LocalDate day) {
        return JdbcOperations.queryOne(dataSource, COMPLETED_ON, rs -> rs.getInt(1), characterId, Date.valueOf(day))
                .orElse(0);
    }

    @Override
    public AcceptOutcome accept(long characterId, LocalDate day, BountyTemplate template, int maxCompletionsPerDay) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            JdbcOperations.lock(connection, LOCK_CHARACTER, characterId);
            if (JdbcOperations.queryOne(connection, ACTIVE, rs -> Boolean.TRUE, characterId)
                    .isPresent()) {
                return AcceptOutcome.ALREADY_ACTIVE;
            }
            int completed = JdbcOperations.queryOne(
                            connection, COMPLETED_ON, rs -> rs.getInt(1), characterId, Date.valueOf(day))
                    .orElse(0);
            if (completed >= maxCompletionsPerDay) {
                return AcceptOutcome.DAILY_LIMIT_REACHED;
            }
            JdbcOperations.update(
                    connection,
                    INSERT,
                    characterId,
                    Date.valueOf(day),
                    template.code(),
                    template.monsterCode(),
                    template.killCount());
            return AcceptOutcome.OK;
        });
    }

    @Override
    public boolean abandon(long characterId) {
        return JdbcOperations.update(dataSource, ABANDON, characterId) > 0;
    }

    @Override
    public Optional<Progress> onKill(long characterId, String monsterCode) {
        return JdbcTransactionRunner.call(dataSource, connection -> onKillIn(connection, characterId, monsterCode));
    }

    private static Optional<Progress> onKillIn(Connection connection, long characterId, String monsterCode) {
        Optional<Row> found = JdbcOperations.queryOne(
                connection,
                LOCK_MATCHING,
                rs -> new Row(rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getLong(5), rs.getLong(6)),
                characterId,
                monsterCode);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Row row = found.get();
        int progress = Math.min(row.target(), row.progress() + 1);
        boolean completed = progress >= row.target();
        JdbcOperations.update(connection, ADVANCE, progress, completed ? "COMPLETED" : "ACTIVE", row.id());
        if (completed) {
            GoldLedger.credit(connection, characterId, row.gold(), GOLD_REASON);
            JdbcOperations.update(connection, ADD_EXP, row.exp(), characterId);
            JdbcOperations.update(
                    connection,
                    AUDIT,
                    characterId,
                    "BOUNTY_COMPLETED",
                    "{\"bounty\":\"" + row.code() + "\",\"gold\":" + row.gold() + ",\"exp\":" + row.exp() + "}",
                    Timestamp.from(Instant.now()));
        }
        return Optional.of(new Progress(
                row.code(),
                progress,
                row.target(),
                completed,
                completed ? row.gold() : 0L,
                completed ? row.exp() : 0L));
    }
}
