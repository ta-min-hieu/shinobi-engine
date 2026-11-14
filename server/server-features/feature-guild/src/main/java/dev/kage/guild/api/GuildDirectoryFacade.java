package dev.kage.guild.api;

import java.util.List;

import dev.kage.guild.domain.GuildDirectoryPort;

/** API tra cứu bang (ADR-393 thâm niên). Chạm DB — Virtual Thread. */
public final class GuildDirectoryFacade {

    /** Không có dữ liệu (composition test). */
    public static final GuildDirectoryFacade DISABLED = new GuildDirectoryFacade(guildId -> List.of());

    private final GuildDirectoryPort port;

    /** Dựng ở composition root. */
    public GuildDirectoryFacade(GuildDirectoryPort port) {
        this.port = port;
    }

    /** ADR-393 — thành viên theo thâm niên, lâu nhất trước. */
    public List<GuildDirectoryPort.Senior> seniority(long guildId) {
        return port.seniority(guildId);
    }
}
