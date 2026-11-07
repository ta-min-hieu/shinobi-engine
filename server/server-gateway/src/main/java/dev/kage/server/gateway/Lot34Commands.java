package dev.kage.server.gateway;

import java.time.Instant;

import dev.kage.protocol.BuybackCommand;
import dev.kage.protocol.BuybackOutcome;
import dev.kage.protocol.BuybackResponse;
import dev.kage.protocol.BuybackView;
import dev.kage.protocol.ListBuybackResponse;
import dev.kage.shop.api.BuybackFacade;
import dev.kage.shop.domain.BuybackPort;

/** Lớp lệnh lô 34 (ADR-199…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot34Commands(Lot34Facades facades) {

    static Lot34Commands of(GatewayFacades gateway) {
        return new Lot34Commands(gateway.progression().expansion().lot34());
    }

    /** ADR-202 — Virtual Thread; món còn mua lại được của {@code characterId}. */
    ListBuybackResponse listBuyback(long characterId) {
        return ListBuybackResponse.newBuilder()
                .addAllEntries(views(characterId, Instant.now()))
                .build();
    }

    /** ADR-202 — Virtual Thread; mua lại một món. */
    BuybackResponse buyBack(long characterId, BuybackCommand command) {
        Instant now = Instant.now();
        BuybackPort.Outcome outcome = facades.buyback().buyBack(characterId, command.getEntryId(), now);
        return BuybackResponse.newBuilder()
                .setOutcome(BuybackOutcome.valueOf("BUYBACK_OUTCOME_" + outcome.name()))
                .addAllEntries(views(characterId, now))
                .build();
    }

    private java.util.List<BuybackView> views(long characterId, Instant now) {
        return facades.buyback().list(characterId, now).stream()
                .map(entry -> BuybackView.newBuilder()
                        .setEntryId(entry.id())
                        .setItemTemplateId(entry.itemTemplateId())
                        .setQuantity(entry.quantity())
                        .setEnhanceLevel(entry.enhanceLevel())
                        .setPrice(entry.price())
                        .setSecondsLeft(Math.max(
                                0L, entry.soldAt().plus(BuybackFacade.WINDOW).getEpochSecond() - now.getEpochSecond()))
                        .build())
                .toList();
    }

    /** ADR-204 — Virtual Thread; bật / tắt (hoặc chỉ hỏi) công tắc tự bán đồ rác khi nhặt. */
    dev.kage.protocol.SetAutoSellJunkResponse setAutoSellJunk(
            long characterId, dev.kage.protocol.SetAutoSellJunkCommand command) {
        if (!command.getQueryOnly()) {
            facades.autoSell().setEnabled(characterId, command.getEnabled());
        }
        return dev.kage.protocol.SetAutoSellJunkResponse.newBuilder()
                .setEnabled(facades.autoSell().enabled(characterId))
                .build();
    }
}
