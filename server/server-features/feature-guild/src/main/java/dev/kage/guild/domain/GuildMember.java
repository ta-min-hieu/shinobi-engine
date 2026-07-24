package dev.kage.guild.domain;

import java.time.Instant;
import java.util.Objects;

/** 1 dòng thành viên — {@code characterId} là UNIQUE ở tầng DB (1 nhân vật tối đa 1 bang). */
public record GuildMember(long guildId, long characterId, GuildRole role, Instant joinedAt) {

    public GuildMember {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }
}
