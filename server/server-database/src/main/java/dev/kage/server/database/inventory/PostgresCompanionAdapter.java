package dev.kage.server.database.inventory;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.inventory.domain.CompanionPort;
import dev.kage.inventory.domain.PetPolicy;
import dev.kage.inventory.domain.PetProgress;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link CompanionPort} (ADR-070/072, V71). Tốc độ phi kiếm đọc MỘT lần (tham số). Linh thú chưa có dòng
 * {@code pet_state} là cấp 1 — dòng chỉ được tạo ở lần nhận EXP đầu tiên.
 */
public final class PostgresCompanionAdapter implements CompanionPort {

    private static final String MOUNT_SPEEDS =
            "SELECT id, move_speed_percent FROM item_template WHERE move_speed_percent > 0";

    private static final String PET_LEVELS = """
            SELECT p.inventory_item_id, p.level
              FROM pet_state p JOIN inventory_item ii ON ii.id = p.inventory_item_id
             WHERE ii.character_id = ?
            """;

    private static final String EQUIPPED_PET = """
            SELECT es.inventory_item_id, ii.item_template_id
              FROM equipment_slot es JOIN inventory_item ii ON ii.id = es.inventory_item_id
             WHERE es.character_id = ? AND es.slot_type = 'PET'
            """;

    private static final String ENSURE_STATE =
            "INSERT INTO pet_state (inventory_item_id, level, exp) VALUES (?, 1, 0) ON CONFLICT DO NOTHING";

    private static final String LOCK_STATE = "SELECT level, exp FROM pet_state WHERE inventory_item_id = ? FOR UPDATE";

    private static final String READ_STATE = "SELECT level, exp FROM pet_state WHERE inventory_item_id = ?";

    private static final String SET_STATE = "UPDATE pet_state SET level = ?, exp = ? WHERE inventory_item_id = ?";

    private record EquippedPet(long inventoryItemId, long itemTemplateId) {}

    private record State(int level, long exp) {}

    private final DataSource dataSource;
    private volatile Map<Long, Integer> speeds;

    public PostgresCompanionAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int moveSpeedPercentOf(long itemTemplateId) {
        Map<Long, Integer> loaded = speeds;
        if (loaded == null) {
            Map<Long, Integer> map = new HashMap<>();
            JdbcOperations.query(dataSource, MOUNT_SPEEDS, rs -> Map.entry(rs.getLong(1), rs.getInt(2)))
                    .forEach(row -> map.put(row.getKey(), row.getValue()));
            loaded = Map.copyOf(map);
            speeds = loaded;
        }
        return loaded.getOrDefault(itemTemplateId, 0);
    }

    @Override
    public Map<Long, Integer> petLevels(long characterId) {
        Map<Long, Integer> levels = new HashMap<>();
        JdbcOperations.query(dataSource, PET_LEVELS, rs -> Map.entry(rs.getLong(1), rs.getInt(2)), characterId)
                .forEach(row -> levels.put(row.getKey(), row.getValue()));
        return levels;
    }

    @Override
    public Optional<PetProgress> grantPetExp(long characterId, long amount) {
        return JdbcTransactionRunner.call(dataSource, connection -> grantIn(connection, characterId, amount));
    }

    private static Optional<PetProgress> grantIn(Connection connection, long characterId, long amount) {
        List<EquippedPet> pets = JdbcOperations.query(
                connection, EQUIPPED_PET, rs -> new EquippedPet(rs.getLong(1), rs.getLong(2)), characterId);
        if (pets.isEmpty()) {
            return Optional.empty();
        }
        EquippedPet pet = pets.getFirst();
        JdbcOperations.update(connection, ENSURE_STATE, pet.inventoryItemId());
        State state = JdbcOperations.queryOne(
                        connection, LOCK_STATE, rs -> new State(rs.getInt(1), rs.getLong(2)), pet.inventoryItemId())
                .orElse(new State(1, 0L));
        PetProgress progress = PetPolicy.gain(
                new PetProgress(pet.inventoryItemId(), pet.itemTemplateId(), state.level(), state.exp(), false),
                amount);
        JdbcOperations.update(connection, SET_STATE, progress.level(), progress.expIntoLevel(), pet.inventoryItemId());
        return Optional.of(progress);
    }

    @Override
    public Optional<PetProgress> equippedPet(long characterId) {
        List<EquippedPet> pets = JdbcOperations.query(
                dataSource, EQUIPPED_PET, rs -> new EquippedPet(rs.getLong(1), rs.getLong(2)), characterId);
        if (pets.isEmpty()) {
            return Optional.empty();
        }
        EquippedPet pet = pets.getFirst();
        State state = JdbcOperations.queryOne(
                        dataSource, READ_STATE, rs -> new State(rs.getInt(1), rs.getLong(2)), pet.inventoryItemId())
                .orElse(new State(1, 0L));
        return Optional.of(
                new PetProgress(pet.inventoryItemId(), pet.itemTemplateId(), state.level(), state.exp(), false));
    }
}
