package dev.kage.guild.domain;

import java.time.Instant;
import java.util.Objects;

/** Aggregate root — 1 bang. Thành viên KHÔNG nhúng ở đây, xem {@link GuildMember}/{@link GuildPort}. */
public record Guild(long id, String name, long leaderCharacterId, Instant createdAt) {

    public Guild {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Tên bang không được rỗng");
        }
    }
}
