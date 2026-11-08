package dev.kage.server.database.monster;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.monster.domain.MonsterInfoPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link MonsterInfoPort} (ADR-255) — {@code monster_template} + {@code drop_table_entry}. */
public final class PostgresMonsterInfoAdapter implements MonsterInfoPort {

    private static final String TEMPLATE = """
            SELECT id, code, name_key, level, max_hp, atk, def, exp_reward, is_boss, drop_table_id
              FROM monster_template WHERE code = ?
            """;
    private static final String DROPS = """
            SELECT item_template_id, drop_chance_percent, min_quantity, max_quantity
              FROM drop_table_entry WHERE drop_table_id = ? ORDER BY drop_chance_percent DESC, id
            """;

    /** Dòng mẫu quái kèm bảng rơi (0 = không có). */
    private record Row(Profile profile, long dropTableId) {}

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresMonsterInfoAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Profile> info(String code) {
        Optional<Row> row = JdbcOperations.queryOne(
                dataSource,
                TEMPLATE,
                rs -> new Row(
                        new Profile(
                                rs.getString("code"),
                                rs.getString("name_key"),
                                rs.getInt("level"),
                                rs.getInt("max_hp"),
                                rs.getInt("atk"),
                                rs.getInt("def"),
                                rs.getLong("exp_reward"),
                                rs.getBoolean("is_boss"),
                                List.of()),
                        rs.getLong("drop_table_id")),
                code);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        Profile base = row.get().profile();
        List<Drop> drops = row.get().dropTableId() == 0L
                ? List.of()
                : JdbcOperations.query(
                        dataSource,
                        DROPS,
                        rs -> new Drop(
                                rs.getLong("item_template_id"),
                                rs.getInt("drop_chance_percent"),
                                rs.getInt("min_quantity"),
                                rs.getInt("max_quantity")),
                        row.get().dropTableId());
        return Optional.of(new Profile(
                base.code(),
                base.nameKey(),
                base.level(),
                base.maxHp(),
                base.atk(),
                base.def(),
                base.expReward(),
                base.boss(),
                drops));
    }
}
