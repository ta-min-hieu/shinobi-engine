package dev.kage.server.database.world;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcOperations;
import dev.kage.world.domain.SafeZone;
import dev.kage.world.domain.SafeZonePort;

/**
 * Hiện thực {@link SafeZonePort} trên PostgreSQL (ADR-046 D3) — đọc thuần, {@code safe_zone} là
 * config tĩnh nạp một lần lúc khởi động, cùng khuôn {@code PostgresNpcPlacementAdapter}.
 */
public final class PostgresSafeZoneAdapter implements SafeZonePort {

    // ORDER BY id giữ thứ tự ổn định giữa các lần khởi động — không ai dựa vào nó, nhưng thứ tự
    // ngẫu nhiên làm test khó tái hiện mà chẳng đổi lấy gì.
    private static final String LIST_SAFE_ZONES = """
            SELECT code, world_code, min_x, min_y, max_x, max_y, heals
              FROM safe_zone
             WHERE world_code = ?
             ORDER BY id
            """;

    private final DataSource dataSource;

    public PostgresSafeZoneAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<dev.kage.world.domain.PvpZone> listPvpZones(String worldCode) {
        return JdbcOperations.query(
                dataSource,
                "SELECT code, world_code, min_x, max_x FROM pvp_zone WHERE world_code = ? ORDER BY min_x",
                rs -> new dev.kage.world.domain.PvpZone(
                        rs.getString(1), rs.getString(2), rs.getFloat(3), rs.getFloat(4)),
                worldCode);
    }

    @Override
    public List<dev.kage.world.domain.WeatherRegion> listWeatherRegions(String worldCode) {
        return JdbcOperations.query(
                dataSource,
                """
                SELECT code, min_x, max_x, kind, period_minutes, active_minutes, offset_minutes
                  FROM weather_region WHERE world_code = ? ORDER BY min_x
                """,
                rs -> new dev.kage.world.domain.WeatherRegion(
                        rs.getString(1),
                        rs.getFloat(2),
                        rs.getFloat(3),
                        rs.getString(4),
                        rs.getInt(5),
                        rs.getInt(6),
                        rs.getInt(7)),
                worldCode);
    }

    @Override
    public List<SafeZone> listSafeZones(String worldCode) {
        return JdbcOperations.query(dataSource, LIST_SAFE_ZONES, PostgresSafeZoneAdapter::mapSafeZone, worldCode);
    }

    private static SafeZone mapSafeZone(ResultSet rs) throws SQLException {
        return new SafeZone(
                rs.getString("code"),
                rs.getString("world_code"),
                rs.getFloat("min_x"),
                rs.getFloat("min_y"),
                rs.getFloat("max_x"),
                rs.getFloat("max_y"),
                rs.getBoolean("heals"));
    }
}
