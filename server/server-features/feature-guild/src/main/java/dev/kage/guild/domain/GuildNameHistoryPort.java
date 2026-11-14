package dev.kage.guild.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Lịch sử tên bang (ADR-423) — bảng {@code guild_name_history}. */
public interface GuildNameHistoryPort {

    /** Một lần đổi tên. */
    record Rename(String oldName, String newName, Instant at) {}

    /** Ghi một lần đổi tên thành công. */
    void record(long guildId, String oldName, String newName, Instant at);

    /** Các lần đổi tên của bang, cũ trước. */
    List<Rename> history(long guildId);

    /** Bang có tên hiện tại HOẶC từng mang tên {@code name} (không phân biệt hoa thường). */
    Optional<Long> findGuildId(String name);
}
