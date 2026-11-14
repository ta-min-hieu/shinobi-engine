package dev.kage.server.database.friend;

import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.friend.domain.FriendRequestNotePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link FriendRequestNotePort} (ADR-376) — bảng {@code friend_request_note} (V181). */
public final class PostgresFriendRequestNoteAdapter implements FriendRequestNotePort {

    private static final String UPSERT = """
            INSERT INTO friend_request_note (requester_id, addressee_id, message) VALUES (?, ?, ?)
            ON CONFLICT (requester_id, addressee_id) DO UPDATE SET message = EXCLUDED.message, created_at = now()
            """;
    private static final String FIND =
            "SELECT message FROM friend_request_note WHERE requester_id = ? AND addressee_id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresFriendRequestNoteAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(long requesterId, long addresseeId, String message) {
        JdbcOperations.update(dataSource, UPSERT, requesterId, addresseeId, message);
    }

    @Override
    public Optional<String> find(long requesterId, long addresseeId) {
        return JdbcOperations.queryOne(dataSource, FIND, rs -> rs.getString(1), requesterId, addresseeId);
    }
}
