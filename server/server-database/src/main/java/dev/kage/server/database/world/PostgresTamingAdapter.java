package dev.kage.server.database.world;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;
import dev.kage.world.domain.TamingPort;

/** Hiện thực {@link TamingPort} (ADR-124, V104). */
public final class PostgresTamingAdapter implements TamingPort {

    private static final String TAMEABLES = """
            SELECT mt.code, mt.level, it.id, it.code, tm.base_chance_percent
              FROM tameable_monster tm
              JOIN monster_template mt ON mt.id = tm.monster_template_id
              JOIN item_template it ON it.id = tm.pet_item_template_id
             ORDER BY mt.id
            """;

    private static final String AUDIT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresTamingAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Tameable> tameables() {
        return JdbcOperations.query(
                dataSource,
                TAMEABLES,
                rs -> new Tameable(rs.getString(1), rs.getInt(2), rs.getLong(3), rs.getString(4), rs.getInt(5)));
    }

    @Override
    public boolean grantPet(long characterId, long petItemTemplateId, String monsterCode) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            CharacterInventoryLock.acquire(connection, characterId);
            if (!InventoryStackMerge.insertRespectingMaxStack(connection, characterId, petItemTemplateId, 1)) {
                return false;
            }
            JdbcOperations.update(
                    connection,
                    AUDIT,
                    characterId,
                    "TAME_MONSTER",
                    "{\"monsterCode\":\"" + monsterCode + "\",\"petItemTemplateId\":" + petItemTemplateId + "}",
                    Timestamp.from(Instant.now()));
            return true;
        });
    }
}
