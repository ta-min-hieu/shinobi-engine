package dev.kage.server.database.inventory;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.inventory.domain.GearRankingPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link GearRankingPort}. */
public final class PostgresGearRankingAdapter implements GearRankingPort {

    private static final String PETS = """
            SELECT c.name, MAX(p.level) AS v FROM pet_state p
              JOIN inventory_item i ON i.id = p.inventory_item_id
              JOIN character c ON c.id = i.character_id
             GROUP BY c.id, c.name ORDER BY v DESC, c.name LIMIT ?
            """;

    private static final String ENHANCE = """
            SELECT c.name, MAX(i.enhance_level) AS v FROM inventory_item i
              JOIN character c ON c.id = i.character_id
             WHERE i.enhance_level > 0
             GROUP BY c.id, c.name ORDER BY v DESC, c.name LIMIT ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresGearRankingAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Ranked> topPets(int limit) {
        return JdbcOperations.query(dataSource, PETS, rs -> new Ranked(rs.getString(1), rs.getLong(2)), limit);
    }

    @Override
    public List<Ranked> topEnhance(int limit) {
        return JdbcOperations.query(dataSource, ENHANCE, rs -> new Ranked(rs.getString(1), rs.getLong(2)), limit);
    }
}
