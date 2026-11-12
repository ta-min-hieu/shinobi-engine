package dev.kage.server.database.character;

import java.sql.Date;
import java.time.LocalDate;

import javax.sql.DataSource;

import dev.kage.character.domain.ProfileLikePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link ProfileLikePort} (ADR-298) — bảng {@code profile_like} (V173); trùng khoá chính ⇒ không ghi. */
public final class PostgresProfileLikeAdapter implements ProfileLikePort {

    private static final String LIKE = """
            INSERT INTO profile_like (liker_id, target_id, liked_on) VALUES (?, ?, ?) ON CONFLICT DO NOTHING
            """;

    private static final String COUNT = "SELECT COUNT(*) FROM profile_like WHERE target_id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresProfileLikeAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean like(long likerId, long targetId, LocalDate day) {
        return JdbcOperations.update(dataSource, LIKE, likerId, targetId, Date.valueOf(day)) == 1;
    }

    @Override
    public int likesOf(long targetId) {
        return JdbcOperations.queryOne(dataSource, COUNT, rs -> rs.getInt(1), targetId)
                .orElse(0);
    }
}
