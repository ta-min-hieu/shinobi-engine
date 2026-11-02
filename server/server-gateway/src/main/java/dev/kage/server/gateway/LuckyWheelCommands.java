package dev.kage.server.gateway;

import java.time.Clock;

import dev.kage.checkin.api.LuckyWheelFacade;
import dev.kage.checkin.domain.LuckyWheelPort;
import dev.kage.checkin.domain.LuckyWheelRules;
import dev.kage.protocol.GetLuckyWheelResponse;
import dev.kage.protocol.SpinLuckyWheelOutcome;
import dev.kage.protocol.SpinLuckyWheelResponse;
import dev.kage.protocol.WheelPrizeView;

/** Vòng quay may mắn (ADR-151) — xem vòng và quay; server chọn ô, trừ tiền, trao thưởng. Ở đâu cũng quay được. */
final class LuckyWheelCommands {

    private final LuckyWheelFacade facade;
    private final Clock clock;

    LuckyWheelCommands(LuckyWheelFacade facade, Clock clock) {
        this.facade = facade;
        this.clock = clock;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    GetLuckyWheelResponse wheel(long characterId) {
        LuckyWheelPort.Wheel wheel = facade.wheel(characterId, clock.instant());
        GetLuckyWheelResponse.Builder response = GetLuckyWheelResponse.newBuilder()
                .setFreeSpinsLeft(wheel.spins().freeLeft())
                .setPaidSpinsLeft(wheel.spins().paidLeft())
                .setSpinCost(LuckyWheelRules.SPIN_COST);
        wheel.prizes().forEach(prize -> response.addPrizes(view(prize)));
        return response.build();
    }

    /** Virtual Thread; {@code characterId} từ session. */
    SpinLuckyWheelResponse spin(long characterId) {
        LuckyWheelPort.Spin spin = facade.spin(characterId, clock.instant());
        SpinLuckyWheelResponse.Builder response = SpinLuckyWheelResponse.newBuilder()
                .setOutcome(wire(spin.outcome()))
                .setPaid(spin.paid())
                .setFreeSpinsLeft(spin.spins().freeLeft())
                .setPaidSpinsLeft(spin.spins().paidLeft());
        spin.prize().ifPresent(prize -> response.setPrize(view(prize)));
        return response.build();
    }

    private static WheelPrizeView view(LuckyWheelPort.Prize prize) {
        return WheelPrizeView.newBuilder()
                .setIndex(prize.index())
                .setLabelKey(prize.labelKey())
                .setGold(prize.gold())
                .setItemTemplateId(prize.itemTemplateId())
                .setQuantity(prize.quantity())
                .build();
    }

    private static SpinLuckyWheelOutcome wire(LuckyWheelPort.Outcome outcome) {
        return switch (outcome) {
            case OK -> SpinLuckyWheelOutcome.SPIN_LUCKY_WHEEL_OUTCOME_OK;
            case NO_SPINS_LEFT -> SpinLuckyWheelOutcome.SPIN_LUCKY_WHEEL_OUTCOME_NO_SPINS_LEFT;
            case INSUFFICIENT_GOLD -> SpinLuckyWheelOutcome.SPIN_LUCKY_WHEEL_OUTCOME_INSUFFICIENT_GOLD;
            case BAG_FULL -> SpinLuckyWheelOutcome.SPIN_LUCKY_WHEEL_OUTCOME_BAG_FULL;
            case GOLD_CAP -> SpinLuckyWheelOutcome.SPIN_LUCKY_WHEEL_OUTCOME_GOLD_CAP;
        };
    }
}
