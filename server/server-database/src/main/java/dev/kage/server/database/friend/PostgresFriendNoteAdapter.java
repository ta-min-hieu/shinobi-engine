package dev.kage.server.database.friend;

import java.util.Map;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import dev.kage.friend.domain.FriendNotePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link FriendNotePort} (ADR-173, V139). */
public final class PostgresFriendNoteAdapter implements FriendNotePort {

    private static final String NOTES = "SELECT friend_id, note FROM friend_note WHERE owner_id = ?";

    private static final String UPSERT = """
            INSERT INTO friend_note (owner_id, friend_id, note, updated_at) VALUES (?, ?, ?, now())
            ON CONFLICT (owner_id, friend_id) DO UPDATE SET note = EXCLUDED.note, updated_at = now()
            """;

    private static final String DELETE = "DELETE FROM friend_note WHERE owner_id = ? AND friend_id = ?";

    private final DataSource dataSource;

    public PostgresFriendNoteAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Map<Long, String> notesOf(long ownerId) {
        return JdbcOperations.query(dataSource, NOTES, rs -> Map.entry(rs.getLong(1), rs.getString(2)), ownerId)
                .stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void put(long ownerId, long friendId, String note) {
        JdbcOperations.update(dataSource, UPSERT, ownerId, friendId, note);
    }

    @Override
    public void remove(long ownerId, long friendId) {
        JdbcOperations.update(dataSource, DELETE, ownerId, friendId);
    }
}
