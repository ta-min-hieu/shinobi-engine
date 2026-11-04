package dev.kage.server.gateway;

import java.time.Clock;
import java.util.List;

import dev.kage.inventory.api.JunkSaleFacade;
import dev.kage.inventory.domain.JunkSalePort;
import dev.kage.protocol.GetJunkPreviewResponse;
import dev.kage.protocol.JunkLineView;
import dev.kage.protocol.SellJunkResponse;

/** Bán nhanh đồ rác (ADR-170) — xem trước + bán hết; server tự tính lại lúc bán. */
final class JunkSaleCommands {

    private final JunkSaleFacade facade;
    private final Clock clock;

    JunkSaleCommands(JunkSaleFacade facade, Clock clock) {
        this.facade = facade;
        this.clock = clock;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    GetJunkPreviewResponse preview(long characterId) {
        GetJunkPreviewResponse.Builder response = GetJunkPreviewResponse.newBuilder();
        views(facade.preview(characterId)).forEach(response::addLines);
        return response.build();
    }

    /** Virtual Thread; {@code characterId} từ session. */
    SellJunkResponse sell(long characterId) {
        JunkSalePort.Sold sold = facade.sellAll(characterId, clock.instant());
        SellJunkResponse.Builder response =
                SellJunkResponse.newBuilder().setGold(sold.gold()).setGoldFull(sold.goldFull());
        views(sold.lines()).forEach(response::addSold);
        return response.build();
    }

    private static List<JunkLineView> views(List<JunkSalePort.Line> lines) {
        return lines.stream()
                .map(line -> JunkLineView.newBuilder()
                        .setItemTemplateId(line.itemTemplateId())
                        .setQuantity(line.quantity())
                        .setPriceEach(line.priceEach())
                        .build())
                .toList();
    }
}
