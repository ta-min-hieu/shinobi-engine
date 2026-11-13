package dev.kage.server.database.guild;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildMemberNotePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link GuildMemberNotePort} (ADR-319) — cột {@code guild_member.officer_note} (V175). */
public final class PostgresGuildMemberNoteAdapter implements GuildMemberNotePort {

    private static final String NOTES =
            "SELECT character_id, officer_note FROM guild_member WHERE guild_id = ? AND officer_note <> ''";
    private static final String SET =
            "UPDATE guild_member SET officer_note = ? WHERE guild_id = ? AND character_id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresGuildMemberNoteAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Map<Long, String> notesOf(long guildId) {
        Map<Long, String> notes = new HashMap<>();
        JdbcOperations.query(dataSource, NOTES, rs -> Map.entry(rs.getLong(1), rs.getString(2)), guildId)
                .forEach(row -> notes.put(row.getKey(), row.getValue()));
        return notes;
    }

    @Override
    public boolean setNote(long guildId, long memberId, String note) {
        return JdbcOperations.update(dataSource, SET, note, guildId, memberId) == 1;
    }
}
