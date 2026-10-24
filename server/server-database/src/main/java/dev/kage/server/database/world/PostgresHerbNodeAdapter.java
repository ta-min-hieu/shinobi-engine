package dev.kage.server.database.world;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcOperations;
import dev.kage.world.domain.HerbNode;
import dev.kage.world.domain.HerbNodePort;

/** Hiện thực {@link HerbNodePort} (ADR-098, V86) — đọc thuần, nạp một lần lúc khởi động. */
public final class PostgresHerbNodeAdapter implements HerbNodePort {

    private static final String LIST = """
            SELECT h.id, h.code, h.x, h.y, h.item_template_id, it.code, h.quantity, h.respawn_seconds
              FROM herb_node h
              JOIN item_template it ON it.id = h.item_template_id
             ORDER BY h.id
            """;

    private final DataSource dataSource;

    public PostgresHerbNodeAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<HerbNode> listHerbNodes() {
        return JdbcOperations.query(
                dataSource,
                LIST,
                rs -> new HerbNode(
                        rs.getLong(1),
                        rs.getString(2),
                        rs.getFloat(3),
                        rs.getFloat(4),
                        rs.getLong(5),
                        rs.getString(6),
                        rs.getInt(7),
                        rs.getInt(8)));
    }
}
