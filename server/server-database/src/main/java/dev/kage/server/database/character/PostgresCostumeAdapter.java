package dev.kage.server.database.character;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.character.domain.CostumePort;
import dev.kage.server.database.GoldLedger;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@link CostumePort} (ADR-166, V134). Trừ phí và ghi lựa chọn trong MỘT transaction, kèm audit. */
public final class PostgresCostumeAdapter implements CostumePort {

    private static final String GOLD_REASON = "gold.reason.costume";

    private static final String CHOSEN = "SELECT costume_tier FROM character_costume_choice WHERE character_id = ?";

    private static final String UPSERT = """
            INSERT INTO character_costume_choice (character_id, costume_tier, chosen_at) VALUES (?, ?, ?)
            ON CONFLICT (character_id)
            DO UPDATE SET costume_tier = EXCLUDED.costume_tier, chosen_at = EXCLUDED.chosen_at
            """;

    private static final String CLEAR = "DELETE FROM character_costume_choice WHERE character_id = ?";

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresCostumeAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Integer> chosenTier(long characterId) {
        return JdbcOperations.queryOne(dataSource, CHOSEN, rs -> rs.getInt(1), characterId);
    }

    @Override
    public Outcome choose(long characterId, int tier, long fee, Instant at) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            if (fee > 0 && !GoldLedger.deduct(connection, characterId, fee, GOLD_REASON)) {
                return Outcome.NOT_ENOUGH_GOLD;
            }
            if (tier == 0) {
                JdbcOperations.update(connection, CLEAR, characterId);
            } else {
                JdbcOperations.update(connection, UPSERT, characterId, tier, Timestamp.from(at));
            }
            JdbcOperations.update(
                    connection,
                    AUDIT,
                    characterId,
                    "COSTUME_CHOSEN",
                    "{\"tier\":" + tier + ",\"gold\":" + fee + "}",
                    Timestamp.from(at));
            return Outcome.OK;
        });
    }
}
