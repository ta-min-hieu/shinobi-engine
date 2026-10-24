package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import dev.kage.guild.api.SpiritVeinFacade;
import dev.kage.guild.domain.SpiritVein;
import dev.kage.guild.domain.SpiritVeinPort;
import dev.kage.guild.domain.SpiritVeinRules;
import dev.kage.protocol.GetSpiritVeinStatusResponse;
import dev.kage.protocol.SpiritVeinScoreView;
import dev.kage.protocol.SpiritVeinView;

/** Bảng tình hình Linh Mạch (ADR-090) — tách khỏi {@code GatewayChannelHandler}. Chạy trên Virtual Thread (chạm DB). */
final class SpiritVeinCommands {

    private final SpiritVeinFacade veins;
    private final Clock clock;

    SpiritVeinCommands(SpiritVeinFacade veins, Clock clock) {
        this.veins = veins;
        this.clock = clock;
    }

    GetSpiritVeinStatusResponse status(long characterId) {
        Instant now = clock.instant();
        GetSpiritVeinStatusResponse.Builder response = GetSpiritVeinStatusResponse.newBuilder();
        for (SpiritVein vein : veins.veins()) {
            Optional<Instant> active = SpiritVeinRules.activeWindowStart(vein.schedule(), now);
            Instant change = active.map(start -> SpiritVeinRules.windowEnd(vein.schedule(), start))
                    .orElseGet(() -> SpiritVeinRules.nextWindowStart(vein.schedule(), now));
            SpiritVeinView.Builder view = SpiritVeinView.newBuilder()
                    .setCode(vein.code())
                    .setNameKey(vein.nameKey())
                    .setMinX(vein.zone().minX())
                    .setMaxX(vein.zone().maxX())
                    .setActive(active.isPresent())
                    .setSecondsUntilChange(
                            Math.max(0L, Duration.between(now, change).toSeconds()));
            veins.holderOf(vein.code(), now).ifPresent(holder -> view.setHolderGuildName(holder.guildName())
                    .setHeldUntilEpochSeconds(holder.heldUntil().getEpochSecond()));
            active.ifPresent(start -> {
                for (SpiritVeinPort.Score score : veins.scores(vein.code(), start)) {
                    view.addScores(SpiritVeinScoreView.newBuilder()
                            .setGuildName(score.guildName())
                            .setPoints(score.points()));
                }
            });
            response.addVeins(view);
        }
        return response.build();
    }
}
