package dev.kage.server.database.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import dev.kage.inventory.domain.EquipmentBonus;
import dev.kage.inventory.domain.ItemSet;
import dev.kage.inventory.domain.ItemSetPort;
import dev.kage.inventory.domain.ItemSetTier;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link ItemSetPort} (ADR-065, V67). Đọc MỘT lần — tham số, đổi thì khởi động lại. */
public final class PostgresItemSetAdapter implements ItemSetPort {

    private static final String SETS = "SELECT id, name_key FROM item_set ORDER BY id";
    private static final String MEMBERS = "SELECT item_set_id, item_template_id FROM item_set_member";
    private static final String BONUSES = """
            SELECT item_set_id, pieces_required, bonus_atk, bonus_def, bonus_res, bonus_hp
              FROM item_set_bonus
            """;

    private final DataSource dataSource;
    private volatile List<ItemSet> sets;

    public PostgresItemSetAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<ItemSet> itemSets() {
        List<ItemSet> loaded = sets;
        if (loaded == null) {
            loaded = load();
            sets = loaded;
        }
        return loaded;
    }

    private List<ItemSet> load() {
        Map<Long, Set<Long>> members = new HashMap<>();
        JdbcOperations.query(dataSource, MEMBERS, rs -> Map.entry(rs.getLong(1), rs.getLong(2)))
                .forEach(row -> members.computeIfAbsent(row.getKey(), id -> new HashSet<>())
                        .add(row.getValue()));
        Map<Long, List<ItemSetTier>> tiers = new HashMap<>();
        JdbcOperations.query(
                        dataSource,
                        BONUSES,
                        rs -> Map.entry(
                                rs.getLong("item_set_id"),
                                new ItemSetTier(
                                        rs.getInt("pieces_required"),
                                        new EquipmentBonus(
                                                rs.getInt("bonus_atk"),
                                                rs.getInt("bonus_def"),
                                                rs.getInt("bonus_res"),
                                                rs.getInt("bonus_hp")))))
                .forEach(row -> tiers.computeIfAbsent(row.getKey(), id -> new ArrayList<>())
                        .add(row.getValue()));
        List<ItemSet> result = new ArrayList<>();
        JdbcOperations.query(dataSource, SETS, rs -> Map.entry(rs.getLong(1), rs.getString(2)))
                .forEach(row -> result.add(new ItemSet(
                        row.getValue(),
                        members.getOrDefault(row.getKey(), Set.of()),
                        tiers.getOrDefault(row.getKey(), List.of()))));
        return List.copyOf(result);
    }
}
