package dev.kage.guild.api;

import java.time.Instant;

import dev.kage.guild.domain.GuildLeadershipPort;

/** Nhường chức bang chủ (ADR-183). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class GuildLeadershipFacade {

    /** Composition không có nhường chức (test). */
    public static final GuildLeadershipFacade DISABLED =
            new GuildLeadershipFacade((leaderId, targetId, at) -> GuildLeadershipPort.Outcome.NOT_IN_GUILD);

    private final GuildLeadershipPort port;

    /** Dựng ở composition root. */
    public GuildLeadershipFacade(GuildLeadershipPort port) {
        this.port = port;
    }

    /** {@code leaderId} trao chức bang chủ cho {@code targetId}. */
    public GuildLeadershipPort.Outcome transfer(long leaderId, long targetId, Instant at) {
        if (leaderId == targetId) {
            return GuildLeadershipPort.Outcome.CANNOT_TRANSFER_TO_SELF;
        }
        return port.transfer(leaderId, targetId, at);
    }
}
