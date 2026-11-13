package dev.kage.guild.domain;

import java.util.Map;

/** Ghi chú thành viên bang (ADR-319) — cột {@code guild_member.officer_note}. */
public interface GuildMemberNotePort {

    /** Ghi chú KHÁC RỖNG của mọi thành viên trong bang: id thành viên ⇒ ghi chú. */
    Map<Long, String> notesOf(long guildId);

    /** Đặt ghi chú cho một thành viên CỦA bang này; {@code false} nếu người đó không ở bang. */
    boolean setNote(long guildId, long memberId, String note);
}
