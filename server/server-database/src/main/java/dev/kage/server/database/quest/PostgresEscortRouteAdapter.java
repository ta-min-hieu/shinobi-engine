package dev.kage.server.database.quest;

import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.quest.domain.EscortRoutePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link EscortRoutePort} (ADR-114, V94) — một dòng {@code escort_route} theo mã quest. */
public final class PostgresEscortRouteAdapter implements EscortRoutePort {

    private static final String ROUTE_OF = """
            SELECT quest_code, start_x, end_x, max_hp, speed_mps FROM escort_route WHERE quest_code = ?
            """;

    private final DataSource dataSource;

    public PostgresEscortRouteAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Route> routeOf(String questCode) {
        return JdbcOperations.queryOne(
                dataSource,
                ROUTE_OF,
                rs -> new Route(rs.getString(1), rs.getFloat(2), rs.getFloat(3), rs.getInt(4), rs.getFloat(5)),
                questCode);
    }
}
