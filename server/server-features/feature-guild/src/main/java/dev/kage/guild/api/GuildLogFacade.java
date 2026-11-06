package dev.kage.guild.api;

import java.util.List;

import dev.kage.guild.domain.GuildLogPort;

/** Nhật ký bang (ADR-180) — {@link #PAGE} dòng gần nhất (giữ 90 ngày). Mọi thành viên xem được. Chạm DB. */
public final class GuildLogFacade {

    /** Số dòng tối đa một lần hỏi. */
    public static final int PAGE = 50;

    /** Composition không có nhật ký bang (test): rỗng. */
    public static final GuildLogFacade DISABLED = new GuildLogFacade((characterId, limit) -> List.of());

    private final GuildLogPort port;

    /** Dựng ở composition root. */
    public GuildLogFacade(GuildLogPort port) {
        this.port = port;
    }

    /** Dòng gần nhất của bang mà {@code characterId} đang ở. */
    public List<GuildLogPort.Entry> recent(long characterId) {
        return port.recent(characterId, PAGE);
    }
}
