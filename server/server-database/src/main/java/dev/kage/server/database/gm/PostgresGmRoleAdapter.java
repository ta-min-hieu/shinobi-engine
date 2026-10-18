package dev.kage.server.database.gm;

import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.gm.domain.GmRole;
import dev.kage.gm.domain.GmRolePort;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link GmRolePort} — CHỈ {@code SELECT} (ADR-052 D8: không đường nào trong game ghi
 * {@code gm_role}; cấp/thu bằng SQL tay).
 */
public final class PostgresGmRoleAdapter implements GmRolePort {

    private static final String FIND_ROLE = "SELECT role FROM gm_role WHERE account_id = ?";

    private final DataSource dataSource;

    public PostgresGmRoleAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<GmRole> findRole(long accountId) {
        return JdbcOperations.queryOne(dataSource, FIND_ROLE, rs -> GmRole.valueOf(rs.getString("role")), accountId);
    }
}
