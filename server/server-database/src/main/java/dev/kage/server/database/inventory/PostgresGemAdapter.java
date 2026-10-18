package dev.kage.server.database.inventory;

import java.sql.Connection;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import dev.kage.inventory.domain.EquipmentBonus;
import dev.kage.inventory.domain.GemPort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link GemPort} (ADR-064, V66). Bảng chỉ số ngọc đọc MỘT lần (tham số — đổi thì khởi động lại). */
public final class PostgresGemAdapter implements GemPort {

    private static final String GEM_BONUSES =
            "SELECT item_template_id, bonus_atk, bonus_def, bonus_res, bonus_hp FROM gem_template";

    private static final String SOCKETS_OF = """
            SELECT s.inventory_item_id, s.gem_item_template_id
              FROM item_socket s JOIN inventory_item ii ON ii.id = s.inventory_item_id
             WHERE ii.character_id = ?
            """;

    private static final String ENSURE_PITY =
            "INSERT INTO gem_pity (character_id, failures) VALUES (?, 0) ON CONFLICT (character_id) DO NOTHING";

    private static final String LOCK_PITY = "SELECT failures FROM gem_pity WHERE character_id = ? FOR UPDATE";
    private static final String SET_PITY = "UPDATE gem_pity SET failures = ? WHERE character_id = ?";

    private static final String SOCKET = """
            INSERT INTO item_socket (inventory_item_id, gem_item_template_id, socketed_at) VALUES (?, ?, ?)
            ON CONFLICT (inventory_item_id)
            DO UPDATE SET gem_item_template_id = EXCLUDED.gem_item_template_id, socketed_at = EXCLUDED.socketed_at
            """;

    private final DataSource dataSource;
    private volatile Map<Long, EquipmentBonus> bonuses;

    public PostgresGemAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Map<Long, EquipmentBonus> gemBonuses() {
        Map<Long, EquipmentBonus> loaded = bonuses;
        if (loaded == null) {
            List<Map.Entry<Long, EquipmentBonus>> rows = JdbcOperations.query(
                    dataSource,
                    GEM_BONUSES,
                    rs -> Map.entry(
                            rs.getLong("item_template_id"),
                            new EquipmentBonus(
                                    rs.getInt("bonus_atk"),
                                    rs.getInt("bonus_def"),
                                    rs.getInt("bonus_res"),
                                    rs.getInt("bonus_hp"))));
            Map<Long, EquipmentBonus> map = new HashMap<>();
            rows.forEach(row -> map.put(row.getKey(), row.getValue()));
            loaded = Map.copyOf(map);
            bonuses = loaded;
        }
        return loaded;
    }

    @Override
    public Map<Long, Long> socketsOf(long characterId) {
        Map<Long, Long> sockets = new HashMap<>();
        JdbcOperations.query(
                        dataSource,
                        SOCKETS_OF,
                        rs -> Map.entry(rs.getLong("inventory_item_id"), rs.getLong("gem_item_template_id")),
                        characterId)
                .forEach(row -> sockets.put(row.getKey(), row.getValue()));
        return sockets;
    }

    @Override
    public int lockFailures(Transaction tx, long characterId) {
        Connection connection = connection(tx);
        JdbcOperations.update(connection, ENSURE_PITY, characterId);
        return JdbcOperations.queryOne(connection, LOCK_PITY, rs -> rs.getInt(1), characterId)
                .orElse(0);
    }

    @Override
    public void setFailures(Transaction tx, long characterId, int failures) {
        JdbcOperations.update(connection(tx), SET_PITY, failures, characterId);
    }

    @Override
    public void socket(Transaction tx, long inventoryItemId, long gemItemTemplateId) {
        JdbcOperations.update(
                connection(tx), SOCKET, inventoryItemId, gemItemTemplateId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static Connection connection(Transaction tx) {
        return ((JdbcTransaction) tx).connection();
    }
}
