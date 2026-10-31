package dev.kage.server.database.world;

import java.io.Serial;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

import javax.sql.DataSource;

import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;
import dev.kage.world.domain.EncounterPort;
import dev.kage.world.domain.EncounterRules;

/**
 * Hiện thực {@link EncounterPort} (ADR-121, V101). Mỗi lệnh là MỘT giao dịch mở đầu bằng khoá túi của nhân vật — vừa
 * đúng thứ tự khoá chung (DATABASE.md §5.1), vừa tuần tự hoá "một kỳ ngộ chờ / trần ngày" giữa các lượt hạ quái chạy
 * song song trên Virtual Thread.
 */
public final class PostgresEncounterAdapter implements EncounterPort {

    private static final String REASON = "gold.reason.encounter";

    private static final String PENDING = """
            SELECT 1 FROM character_encounter
             WHERE character_id = ? AND resolved_at IS NULL AND expires_at > ?
            """;

    private static final String COUNT_SINCE =
            "SELECT COUNT(*) FROM character_encounter WHERE character_id = ? AND created_at >= ?";

    private static final String ENCOUNTERS = "SELECT id, prompt_key, weight FROM encounter ORDER BY id";

    private static final String INSERT = """
            INSERT INTO character_encounter (character_id, encounter_id, created_at, expires_at)
            VALUES (?, ?, ?, ?) RETURNING id
            """;

    private static final String OPTIONS =
            "SELECT option_index, label_key FROM encounter_option WHERE encounter_id = ? ORDER BY option_index";

    private static final String LOCK_OPEN = """
            SELECT encounter_id FROM character_encounter
             WHERE id = ? AND character_id = ? AND resolved_at IS NULL AND expires_at > ?
               FOR UPDATE
            """;

    private static final String OPTION = """
            SELECT result_key, reward_gold, COALESCE(reward_item_template_id, 0), reward_quantity, spawn_monster_code
              FROM encounter_option
             WHERE encounter_id = ? AND option_index = ?
            """;

    private static final String RESOLVE =
            "UPDATE character_encounter SET resolved_at = ?, chosen_option = ? WHERE id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    /** Một dòng kỳ ngộ để chọn theo trọng số. */
    private record Row(long id, String promptKey, int weight) {}

    /** Một lệnh chọn. */
    private record Choice(long characterId, long instanceId, int optionIndex, Instant now) {}

    private final DataSource dataSource;

    public PostgresEncounterAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Offer> tryStart(long characterId, Instant now, Instant dayStart, ToIntFunction<int[]> pick) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            CharacterInventoryLock.acquire(connection, characterId);
            if (JdbcOperations.queryOne(connection, PENDING, rs -> true, characterId, Timestamp.from(now))
                    .isPresent()) {
                return Optional.<Offer>empty();
            }
            long today = JdbcOperations.queryOne(
                            connection, COUNT_SINCE, rs -> rs.getLong(1), characterId, Timestamp.from(dayStart))
                    .orElse(0L);
            List<Row> rows = JdbcOperations.query(
                    connection, ENCOUNTERS, rs -> new Row(rs.getLong(1), rs.getString(2), rs.getInt(3)));
            if (today >= EncounterRules.DAILY_LIMIT || rows.isEmpty()) {
                return Optional.<Offer>empty();
            }
            Row chosen =
                    rows.get(pick.applyAsInt(rows.stream().mapToInt(Row::weight).toArray()));
            Instant expiresAt = now.plus(EncounterRules.EXPIRY);
            long instanceId = JdbcOperations.queryOne(
                            connection,
                            INSERT,
                            rs -> rs.getLong(1),
                            characterId,
                            chosen.id(),
                            Timestamp.from(now),
                            Timestamp.from(expiresAt))
                    .orElseThrow();
            List<Option> options = JdbcOperations.query(
                    connection, OPTIONS, rs -> new Option(rs.getInt(1), rs.getString(2)), chosen.id());
            return Optional.of(new Offer(instanceId, chosen.promptKey(), options, expiresAt));
        });
    }

    @Override
    public Resolution choose(long characterId, long instanceId, int optionIndex, Instant now) {
        try {
            return JdbcTransactionRunner.call(
                    dataSource,
                    connection -> chooseIn(connection, new Choice(characterId, instanceId, optionIndex, now)));
        } catch (Abort abort) {
            return Resolution.refused(abort.outcome);
        }
    }

    private static Resolution chooseIn(Connection connection, Choice choice) {
        long characterId = choice.characterId();
        long instanceId = choice.instanceId();
        int optionIndex = choice.optionIndex();
        Instant now = choice.now();
        CharacterInventoryLock.acquire(connection, characterId);
        long encounterId = JdbcOperations.queryOne(
                        connection, LOCK_OPEN, rs -> rs.getLong(1), instanceId, characterId, Timestamp.from(now))
                .orElseThrow(() -> new Abort(Outcome.NOT_FOUND));
        Resolution resolution = JdbcOperations.queryOne(
                        connection,
                        OPTION,
                        rs -> new Resolution(
                                Outcome.OK,
                                rs.getString(1),
                                rs.getLong(2),
                                rs.getLong(3),
                                rs.getInt(4),
                                Optional.ofNullable(rs.getString(5))),
                        encounterId,
                        optionIndex)
                .orElseThrow(() -> new Abort(Outcome.INVALID_OPTION));
        if (resolution.itemTemplateId() != 0
                && !InventoryStackMerge.mergeIntoExistingStack(
                        connection, characterId, resolution.itemTemplateId(), resolution.quantity())
                && !InventoryStackMerge.insertRespectingMaxStack(
                        connection, characterId, resolution.itemTemplateId(), resolution.quantity())) {
            throw new Abort(Outcome.BAG_FULL);
        }
        if (resolution.gold() > 0) {
            GoldLedger.credit(connection, characterId, resolution.gold(), REASON);
        }
        JdbcOperations.update(connection, RESOLVE, Timestamp.from(now), optionIndex, instanceId);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "ENCOUNTER_CHOICE",
                "{\"instanceId\":" + instanceId + ",\"encounterId\":" + encounterId + ",\"option\":" + optionIndex
                        + ",\"gold\":" + resolution.gold() + ",\"itemTemplateId\":" + resolution.itemTemplateId()
                        + ",\"quantity\":" + resolution.quantity() + "}",
                Timestamp.from(now));
        return resolution;
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
