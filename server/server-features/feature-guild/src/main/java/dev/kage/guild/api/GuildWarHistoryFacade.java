package dev.kage.guild.api;

import java.util.List;

import dev.kage.guild.domain.GuildWarHistoryPort;

/** API lịch sử bang chiến (ADR-208). */
public final class GuildWarHistoryFacade {

    /** Số trận gần nhất trả về. */
    public static final int LIMIT = 20;

    /** Không bang nào có lịch sử (composition test). */
    public static final GuildWarHistoryFacade DISABLED = new GuildWarHistoryFacade((characterId, limit) -> List.of());

    private final GuildWarHistoryPort port;

    /** Dựng ở composition root. */
    public GuildWarHistoryFacade(GuildWarHistoryPort port) {
        this.port = port;
    }

    /** {@value #LIMIT} trận đã chốt gần nhất của bang {@code characterId}, mới nhất trước. */
    public List<GuildWarHistoryPort.PastWar> recentOf(long characterId) {
        return port.recentOf(characterId, LIMIT);
    }
}
