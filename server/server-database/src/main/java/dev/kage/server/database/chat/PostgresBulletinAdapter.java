package dev.kage.server.database.chat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.chat.domain.BulletinPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link BulletinPort} (ADR-336) — bảng {@code bulletin_post} (V177). */
public final class PostgresBulletinAdapter implements BulletinPort {

    private static final String POSTED_AT = "SELECT posted_at FROM bulletin_post WHERE character_id = ?";
    private static final String UPSERT = """
            INSERT INTO bulletin_post (character_id, body, posted_at) VALUES (?, ?, ?)
            ON CONFLICT (character_id) DO UPDATE SET body = EXCLUDED.body, posted_at = EXCLUDED.posted_at
            """;
    private static final String DELETE = "DELETE FROM bulletin_post WHERE character_id = ?";
    private static final String SINCE = """
            SELECT character_id, body, posted_at FROM bulletin_post WHERE posted_at >= ?
             ORDER BY posted_at DESC, character_id LIMIT ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresBulletinAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Instant> postedAt(long characterId) {
        return JdbcOperations.queryOne(
                dataSource, POSTED_AT, rs -> rs.getTimestamp(1).toInstant(), characterId);
    }

    @Override
    public void post(long characterId, String body, Instant at) {
        JdbcOperations.update(dataSource, UPSERT, characterId, body, Timestamp.from(at));
    }

    @Override
    public boolean remove(long characterId) {
        return JdbcOperations.update(dataSource, DELETE, characterId) == 1;
    }

    @Override
    public List<Post> since(Instant since, int limit) {
        return JdbcOperations.query(
                dataSource,
                SINCE,
                rs -> new Post(
                        rs.getLong("character_id"),
                        rs.getString("body"),
                        rs.getTimestamp("posted_at").toInstant()),
                Timestamp.from(since),
                limit);
    }
}
