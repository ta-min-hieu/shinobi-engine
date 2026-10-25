package dev.kage.guild.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.kage.guild.domain.GuildWar;
import dev.kage.guild.domain.GuildWarOutcome;
import dev.kage.guild.domain.GuildWarPort;

/** API bang chiến (ADR-102). Chạm DB — chỉ gọi trên Virtual Thread hoặc luồng nền. */
public final class GuildWarFacade {

    /** Composition không có bang chiến (test) — không trận nào, tuyên luôn bị từ chối. */
    public static final GuildWarFacade DISABLED = new GuildWarFacade(new GuildWarPort() {
        @Override
        public Declared declare(Declaration declaration) {
            return new Declared(GuildWarOutcome.NOT_IN_GUILD, Optional.empty());
        }

        @Override
        public List<GuildWar> unsettled() {
            return List.of();
        }

        @Override
        public List<Combatant> combatants(Instant now) {
            return List.of();
        }

        @Override
        public boolean recordKill(long killerId, long victimId, Instant at) {
            return false;
        }

        @Override
        public Optional<GuildWar> settle(long warId, Instant now) {
            return Optional.empty();
        }

        @Override
        public List<Payout> pendingPayouts() {
            return List.of();
        }

        @Override
        public void markPaid(Payout payout) {
            // Hàng đợi luôn rỗng.
        }
    });

    private final GuildWarPort port;

    /** Dựng ở composition root. */
    public GuildWarFacade(GuildWarPort port) {
        this.port = port;
    }

    /** Tuyên chiến — xem {@link GuildWarPort#declare}. */
    public GuildWarPort.Declared declare(long leaderId, String targetGuildName, Instant at) {
        return port.declare(new GuildWarPort.Declaration(leaderId, targetGuildName, at));
    }

    /** Mọi trận chưa chốt. */
    public List<GuildWar> unsettled() {
        return port.unsettled();
    }

    /** Thành viên mọi bang đang trong giờ chiến. */
    public List<GuildWarPort.Combatant> combatants(Instant now) {
        return port.combatants(now);
    }

    /** Ghi một lần hạ — xem {@link GuildWarPort#recordKill}. */
    public boolean recordKill(long killerId, long victimId, Instant at) {
        return port.recordKill(killerId, victimId, at);
    }

    /** Chốt trận — xem {@link GuildWarPort#settle}. */
    public Optional<GuildWar> settle(long warId, Instant now) {
        return port.settle(warId, now);
    }

    /** Thư thưởng còn chờ gửi. */
    public List<GuildWarPort.Payout> pendingPayouts() {
        return port.pendingPayouts();
    }

    /** Đánh dấu đã gửi. */
    public void markPaid(GuildWarPort.Payout payout) {
        port.markPaid(payout);
    }
}
