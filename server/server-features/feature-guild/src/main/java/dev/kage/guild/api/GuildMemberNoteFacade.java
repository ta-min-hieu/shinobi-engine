package dev.kage.guild.api;

import java.util.Map;

import dev.kage.guild.domain.GuildMemberNotePort;

/** API ghi chú thành viên bang (ADR-319). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class GuildMemberNoteFacade {

    /** Dài nhất (khớp {@code VARCHAR(100)}). */
    public static final int MAX_LENGTH = 100;

    /** Không lưu gì (composition test). */
    public static final GuildMemberNoteFacade DISABLED = new GuildMemberNoteFacade(new GuildMemberNotePort() {
        @Override
        public Map<Long, String> notesOf(long guildId) {
            return Map.of();
        }

        @Override
        public boolean setNote(long guildId, long memberId, String note) {
            return false;
        }
    });

    private final GuildMemberNotePort port;

    /** Dựng ở composition root. */
    public GuildMemberNoteFacade(GuildMemberNotePort port) {
        this.port = port;
    }

    /** Ghi chú khác rỗng của bang. */
    public Map<Long, String> notesOf(long guildId) {
        return port.notesOf(guildId);
    }

    /** Đặt ghi chú (người gọi đã kiểm quyền + nội dung). */
    public boolean setNote(long guildId, long memberId, String note) {
        return port.setNote(guildId, memberId, note);
    }
}
