package dev.kage.guild.domain;

import java.time.Instant;

/** Đếm số đợt thư toàn bang đã gửi (ADR-235) — để giữ trần mỗi ngày. */
public interface GuildMailQuotaPort {

    /** Số đợt {@code guildId} đã gửi từ {@code since}. */
    int sentSince(long guildId, Instant since);

    /** Ghi một đợt (người gửi, số người nhận). */
    void record(long guildId, long senderId, int recipients, Instant at);
}
