package dev.kage.server.database.guild;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;

import dev.kage.server.database.JdbcOperations;

/**
 * Ghi một dòng nhật ký bang (ADR-180, V143) trên CHÍNH connection của transaction đang làm việc đó — cùng commit, cùng
 * rollback. Mọi adapter bang ghi qua đây; rời/bị đuổi ghi bằng một câu {@code DELETE … RETURNING} gộp (xem
 * {@code PostgresGuildAdapter}).
 */
final class GuildLog {

    /** Loại dòng — khớp CHECK của cột {@code kind}. */
    enum Kind {
        CREATED,
        JOINED,
        LEFT,
        KICKED,
        DONATED,
        LEVEL_UP,
        RENAMED,
        NOTICE_CHANGED,
        /** ADR-183 — actor = bang chủ cũ, subject = bang chủ mới. */
        LEADER_CHANGED,
        /** ADR-184 — actor = bang chủ, subject = người được bổ nhiệm / bãi nhiệm. */
        VICE_APPOINTED,
        VICE_DISMISSED
    }

    /**
     * Một dòng. {@code actorId} = người làm, {@code subjectId} = người chịu (0 = không có).
     *
     * @param amount vàng góp ({@code DONATED}), cấp mới ({@code LEVEL_UP}); 0 nếu không có
     * @param detail tên mới ({@code RENAMED}); "" nếu không có
     */
    record Entry(long guildId, Kind kind, long actorId, long subjectId, long amount, String detail) {

        static Entry of(long guildId, Kind kind, long actorId) {
            return new Entry(guildId, kind, actorId, 0L, 0L, "");
        }
    }

    private static final String APPEND = """
            INSERT INTO guild_log (guild_id, kind, actor_id, subject_id, amount, detail, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private GuildLog() {}

    static void append(Connection connection, Entry entry, Instant at) {
        JdbcOperations.update(
                connection,
                APPEND,
                entry.guildId(),
                entry.kind().name(),
                entry.actorId() == 0L ? null : entry.actorId(),
                entry.subjectId() == 0L ? null : entry.subjectId(),
                entry.amount(),
                entry.detail(),
                Timestamp.from(at));
    }
}
