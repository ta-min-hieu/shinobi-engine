package dev.kage.server.gateway;

import java.time.Clock;

import dev.kage.checkin.api.FortuneFacade;
import dev.kage.checkin.domain.FortunePort;
import dev.kage.checkin.domain.FortuneRules;
import dev.kage.protocol.DrawFortuneOutcome;
import dev.kage.protocol.DrawFortuneResponse;

/** Quẻ bói hằng ngày (ADR-149) — gieo quẻ khi đứng gần trưởng lão Chu Hoành; server tung quẻ và cấp quà. */
final class FortuneCommands {

    private final FortuneFacade facade;
    private final NpcProximity proximity;
    private final Clock clock;

    FortuneCommands(FortuneFacade facade, NpcProximity proximity, Clock clock) {
        this.facade = facade;
        this.proximity = proximity;
        this.clock = clock;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    DrawFortuneResponse draw(long characterId) {
        if (!proximity.isWithinReach(characterId, FortuneRules.NPC_CODE)) {
            return DrawFortuneResponse.newBuilder()
                    .setOutcome(DrawFortuneOutcome.DRAW_FORTUNE_OUTCOME_TOO_FAR)
                    .build();
        }
        FortunePort.Draw draw = facade.draw(characterId, clock.instant());
        DrawFortuneResponse.Builder response = DrawFortuneResponse.newBuilder().setOutcome(wire(draw.outcome()));
        draw.fortune().ifPresent(fortune -> {
            response.setFortuneCode(fortune.code())
                    .setNameKey(fortune.nameKey())
                    .setTextKey(fortune.textKey())
                    .setExpPercent(fortune.expPercent());
            if (draw.outcome() == FortunePort.Outcome.OK) {
                response.setGold(fortune.gold())
                        .setItemTemplateId(fortune.itemTemplateId())
                        .setQuantity(fortune.quantity());
            }
        });
        return response.build();
    }

    private static DrawFortuneOutcome wire(FortunePort.Outcome outcome) {
        return switch (outcome) {
            case OK -> DrawFortuneOutcome.DRAW_FORTUNE_OUTCOME_OK;
            case ALREADY_DRAWN -> DrawFortuneOutcome.DRAW_FORTUNE_OUTCOME_ALREADY_DRAWN;
            case BAG_FULL -> DrawFortuneOutcome.DRAW_FORTUNE_OUTCOME_BAG_FULL;
        };
    }
}
