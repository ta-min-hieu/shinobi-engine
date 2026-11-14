package dev.kage.guild.domain;

import java.time.Instant;
import java.util.List;

/** Tra cứu bang (ADR-393 thâm niên) — đọc {@code guild_member.joined_at}. */
public interface GuildDirectoryPort {

    /** Một thành viên và lúc vào bang. */
    record Senior(String name, Instant joinedAt) {}

    /** Thành viên của bang, vào sớm nhất trước. */
    List<Senior> seniority(long guildId);
}
