package dev.kage.guild.api;

import java.time.Instant;

import dev.kage.guild.domain.GuildLeadershipPort;

/** Nhường chức bang chủ (ADR-183). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class GuildLeadershipFacade {

    /** Composition không có nhường chức (test). */
    public static final GuildLeadershipFacade DISABLED = new GuildLeadershipFacade(new GuildLeadershipPort() {
        @Override
        public Outcome transfer(long leaderId, long targetId, Instant at) {
            return Outcome.NOT_IN_GUILD;
        }

        @Override
        public ViceOutcome setViceLeader(long leaderId, long targetId, boolean appoint, Instant at) {
            return ViceOutcome.NOT_IN_GUILD;
        }
    });

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

    /** ADR-184 — bổ nhiệm ({@code appoint}) / bãi nhiệm phó bang chủ. */
    public GuildLeadershipPort.ViceOutcome setViceLeader(long leaderId, long targetId, boolean appoint, Instant at) {
        return port.setViceLeader(leaderId, targetId, appoint, at);
    }
}
