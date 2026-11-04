package dev.kage.server.gateway;

import java.time.Clock;
import java.util.function.LongToIntFunction;

import dev.kage.inventory.api.GemRemovalFacade;
import dev.kage.inventory.domain.GemRemovalPort;
import dev.kage.protocol.UnsocketGemCommand;
import dev.kage.protocol.UnsocketGemOutcome;
import dev.kage.protocol.UnsocketGemResponse;

/** Lớp lệnh lô 30 (ADR-167…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot30Commands(MailBulkCommands mailBulk, Lot30Facades facades, Clock clock) {

    static Lot30Commands of(GatewayFacades gateway, WorldGateway worldGateway) {
        return new Lot30Commands(
                new MailBulkCommands(gateway.contentFacades().socialFacades().mailFacade()),
                gateway.progression().expansion().lot30(),
                Clock.systemUTC());
    }

    /**
     * ADR-168 — Virtual Thread; {@code characterId} từ session. {@code reapplyStats} áp lại chỉ số trang bị (món đang
     * mặc vừa mất viên ngọc) và trả máu tối đa mới — chỉ gateway handler làm được việc đó.
     */
    UnsocketGemResponse unsocketGem(long characterId, UnsocketGemCommand command, LongToIntFunction reapplyStats) {
        GemRemovalPort.Removed removed =
                facades.gemRemoval().unsocket(characterId, command.getItemId(), clock.instant());
        UnsocketGemResponse.Builder response = UnsocketGemResponse.newBuilder()
                .setOutcome(UnsocketGemOutcome.valueOf(
                        "UNSOCKET_GEM_OUTCOME_" + removed.outcome().name()))
                .setGemItemTemplateId(removed.gemItemTemplateId());
        if (removed.outcome() == GemRemovalPort.Outcome.OK) {
            response.setFee(GemRemovalFacade.UNSOCKET_FEE).setMaxHp(reapplyStats.applyAsInt(characterId));
        }
        return response.build();
    }
}
