package dev.kage.server.database.character;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;

import dev.kage.character.domain.Faction;
import dev.kage.character.domain.FactionChangePort;
import dev.kage.character.domain.Transaction;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryItemTake;

/** Hiện thực {@link FactionChangePort} (ADR-066, V68) — {@code character.faction_changes} + Tẩy Căn Đan. */
public final class PostgresFactionChangeAdapter implements FactionChangePort {

    private static final String LOCK_COUNT = "SELECT faction_changes FROM character WHERE id = ? FOR UPDATE";
    private static final String RECORD =
            "UPDATE character SET faction = ?, faction_changes = faction_changes + 1 WHERE id = ?";
    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    @Override
    public void lockInventory(Transaction tx, long characterId) {
        CharacterInventoryLock.acquire(connection(tx), characterId);
    }

    @Override
    public int lockChangeCount(Transaction tx, long characterId) {
        return JdbcOperations.queryOne(connection(tx), LOCK_COUNT, rs -> rs.getInt(1), characterId)
                .orElse(0);
    }

    @Override
    public boolean takePill(Transaction tx, long characterId) {
        return InventoryItemTake.takeByCode(connection(tx), characterId, PILL_CODE, 1);
    }

    @Override
    public void recordChange(Transaction tx, long characterId, Faction faction, boolean pillUsed) {
        Connection connection = connection(tx);
        JdbcOperations.update(connection, RECORD, faction.name(), characterId);
        JdbcOperations.update(
                connection,
                AUDIT,
                characterId,
                "FACTION_CHANGE",
                "{\"faction\":\"" + faction.name() + "\",\"pillUsed\":" + pillUsed + "}",
                Timestamp.from(Instant.now()));
    }

    private static Connection connection(Transaction tx) {
        return ((JdbcTransaction) tx).connection();
    }
}
