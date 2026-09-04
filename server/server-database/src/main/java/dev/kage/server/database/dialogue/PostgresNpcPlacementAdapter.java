package dev.kage.server.database.dialogue;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.dialogue.domain.NpcPlacement;
import dev.kage.dialogue.domain.NpcPlacementPort;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link NpcPlacementPort} trên PostgreSQL (ADR-040) — đọc thuần, {@code npc_spawn} là
 * config tĩnh nạp một lần lúc khởi động.
 */
public final class PostgresNpcPlacementAdapter implements NpcPlacementPort {

    // INNER JOIN an toàn ở đây (khác PostgresMonsterCatalogAdapter phải LEFT JOIN drop_table):
    // npc_spawn.npc_template_id là NOT NULL + FK, nên mọi hàng LUÔN khớp đúng một npc_template.
    // ORDER BY ms.id giữ thứ tự ổn định giữa các lần khởi động — không ai dựa vào nó, nhưng thứ tự
    // spawn ngẫu nhiên làm test khó tái hiện mà chẳng đổi lấy gì.
    private static final String LIST_PLACEMENTS = """
            SELECT nt.code AS npc_code, ns.world_code, ns.pos_x, ns.pos_y
              FROM npc_spawn ns
              JOIN npc_template nt ON nt.id = ns.npc_template_id
             WHERE ns.world_code = ?
             ORDER BY ns.id
            """;

    private final DataSource dataSource;

    public PostgresNpcPlacementAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<NpcPlacement> listPlacements(String worldCode) {
        return JdbcOperations.query(dataSource, LIST_PLACEMENTS, PostgresNpcPlacementAdapter::mapPlacement, worldCode);
    }

    private static NpcPlacement mapPlacement(ResultSet rs) throws SQLException {
        return new NpcPlacement(
                rs.getString("npc_code"), rs.getString("world_code"), rs.getFloat("pos_x"), rs.getFloat("pos_y"));
    }
}
