package dev.kage.guild.domain;

import java.time.Instant;
import java.util.List;

/** Tra cứu bang (ADR-393 thâm niên, ADR-399 tìm theo tên). */
public interface GuildDirectoryPort {

    /** Một thành viên và lúc vào bang. */
    record Senior(String name, Instant joinedAt) {}

    /** Thành viên của bang, vào sớm nhất trước. */
    List<Senior> seniority(long guildId);

    /** ADR-399 — một bang tìm thấy. */
    record Found(String name, int level, int members, String leaderName, String tag) {}

    /**
     * ADR-399 — bang có tên chứa {@code fragment} (không phân biệt hoa thường), cấp cao trước, tối đa {@code limit}.
     */
    List<Found> search(String fragment, int limit);
}
