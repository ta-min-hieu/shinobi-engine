package dev.kage.server.database.friend;

import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.friend.domain.LoginGreetingPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link LoginGreetingPort} (ADR-425) — bảng {@code login_greeting} (V184). */
public final class PostgresLoginGreetingAdapter implements LoginGreetingPort {

    private static final String FIND = "SELECT message FROM login_greeting WHERE character_id = ?";
    private static final String UPSERT = """
            INSERT INTO login_greeting (character_id, message) VALUES (?, ?)
            ON CONFLICT (character_id) DO UPDATE SET message = EXCLUDED.message
            """;
    private static final String DELETE = "DELETE FROM login_greeting WHERE character_id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresLoginGreetingAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<String> greeting(long characterId) {
        return JdbcOperations.queryOne(dataSource, FIND, rs -> rs.getString(1), characterId);
    }

    @Override
    public void setGreeting(long characterId, String message) {
        if (message.isEmpty()) {
            JdbcOperations.update(dataSource, DELETE, characterId);
        } else {
            JdbcOperations.update(dataSource, UPSERT, characterId, message);
        }
    }
}
