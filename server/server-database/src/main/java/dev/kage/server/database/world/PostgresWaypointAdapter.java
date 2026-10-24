package dev.kage.server.database.world;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;
import dev.kage.server.database.inventory.TemplateQuantityConsumer;
import dev.kage.world.domain.Waypoint;
import dev.kage.world.domain.WaypointPort;

/**
 * Hiện thực {@link WaypointPort} (ADR-100, V87). Trừ/trả phù dưới khoá túi đồ chung ({@link CharacterInventoryLock},
 * DATABASE.md §5.1) bằng cùng helper của thư.
 */
public final class PostgresWaypointAdapter implements WaypointPort {

    private static final String LIST = """
            SELECT code, name_key, x, y, sort_order, always_open FROM waypoint ORDER BY sort_order, code
            """;

    private static final String UNLOCKED = "SELECT waypoint_code FROM character_waypoint WHERE character_id = ?";

    private static final String UNLOCK = """
            INSERT INTO character_waypoint (character_id, waypoint_code) VALUES (?, ?) ON CONFLICT DO NOTHING
            """;

    private static final String TEMPLATE_ID = "SELECT id FROM item_template WHERE code = ?";

    private final DataSource dataSource;

    public PostgresWaypointAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Waypoint> listWaypoints() {
        return JdbcOperations.query(
                dataSource,
                LIST,
                rs -> new Waypoint(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getFloat(3),
                        rs.getFloat(4),
                        rs.getInt(5),
                        rs.getBoolean(6)));
    }

    @Override
    public Set<String> unlockedBy(long characterId) {
        return new HashSet<>(JdbcOperations.query(dataSource, UNLOCKED, rs -> rs.getString(1), characterId));
    }

    @Override
    public boolean unlock(long characterId, String waypointCode) {
        return JdbcOperations.update(dataSource, UNLOCK, characterId, waypointCode) > 0;
    }

    @Override
    public boolean consumeTalisman(long characterId, String talismanCode) {
        Optional<Long> templateId = templateId(talismanCode);
        if (templateId.isEmpty()) {
            return false;
        }
        return JdbcTransactionRunner.call(dataSource, connection -> {
            CharacterInventoryLock.acquire(connection, characterId);
            return TemplateQuantityConsumer.consume(connection, characterId, templateId.get(), 1);
        });
    }

    @Override
    public void refundTalisman(long characterId, String talismanCode) {
        templateId(talismanCode)
                .ifPresent(id -> JdbcTransactionRunner.run(dataSource, connection -> {
                    CharacterInventoryLock.acquire(connection, characterId);
                    if (!InventoryStackMerge.mergeIntoExistingStack(connection, characterId, id, 1)) {
                        InventoryStackMerge.insertRespectingMaxStack(connection, characterId, id, 1);
                    }
                }));
    }

    private Optional<Long> templateId(String code) {
        return JdbcOperations.queryOne(dataSource, TEMPLATE_ID, rs -> rs.getLong(1), code);
    }
}
