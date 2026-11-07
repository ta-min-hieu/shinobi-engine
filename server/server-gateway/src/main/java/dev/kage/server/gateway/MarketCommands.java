package dev.kage.server.gateway;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import dev.kage.market.api.MarketFacade;
import dev.kage.market.application.MarketUseCase;
import dev.kage.market.domain.ListingRequest;
import dev.kage.market.domain.MarketListing;
import dev.kage.market.domain.MarketOutcome;
import dev.kage.market.domain.MarketPolicy;
import dev.kage.protocol.BuyListingCommand;
import dev.kage.protocol.BuyListingResponse;
import dev.kage.protocol.CancelListingCommand;
import dev.kage.protocol.CancelListingResponse;
import dev.kage.protocol.CreateListingCommand;
import dev.kage.protocol.CreateListingResponse;
import dev.kage.protocol.ListMarketRequest;
import dev.kage.protocol.ListMarketResponse;
import dev.kage.protocol.ListingSoldEvent;
import dev.kage.protocol.MarketListingView;
import dev.kage.protocol.MarketResult;
import dev.kage.protocol.Opcode;

/**
 * Bốn lệnh phường thị (ADR-063) — tách khỏi {@code GatewayChannelHandler}. Chạy trên Virtual Thread (chạm
 * DB). Khoá chống trùng đã được kiểm HÌNH DẠNG ở EventLoop trước khi tới đây.
 */
final class MarketCommands {

    private static final Map<MarketOutcome, MarketResult> RESULTS = new EnumMap<>(MarketOutcome.class);

    static {
        for (MarketOutcome outcome : MarketOutcome.values()) {
            RESULTS.put(outcome, MarketResult.valueOf("MARKET_RESULT_" + outcome.name()));
        }
    }

    private final MarketFacade market;
    private final ConnectedCharacterRegistry registry;
    private final dev.kage.market.api.MarketWatchFacade watch;

    MarketCommands(
            MarketFacade market, ConnectedCharacterRegistry registry, dev.kage.market.api.MarketWatchFacade watch) {
        this.market = market;
        this.registry = registry;
        this.watch = watch;
    }

    /** Dựng từ composition — tách khỏi constructor handler vì trần {@code MethodLength}. */
    static MarketCommands of(GatewayFacades facades, WorldGateway worldGateway) {
        return new MarketCommands(
                facades.progression().marketFacade(),
                worldGateway.registry(),
                facades.progression().expansion().lot33().marketWatch());
    }

    ListMarketResponse list(long characterId, ListMarketRequest request) {
        Optional<Long> filter =
                request.getItemTemplateId() > 0 ? Optional.of(request.getItemTemplateId()) : Optional.empty();
        ListMarketResponse.Builder response = ListMarketResponse.newBuilder()
                .setMaxActiveListings(MarketPolicy.MAX_ACTIVE_LISTINGS)
                .setFeePercent(MarketPolicy.FEE_PERCENT);
        for (MarketListing listing : market.browse(filter)) {
            if (listing.sellerId() != characterId) {
                response.addListings(view(listing));
            }
        }
        for (MarketListing listing : market.mine(characterId)) {
            response.addMine(view(listing));
        }
        return response.build();
    }

    CreateListingResponse create(long characterId, CreateListingCommand command) {
        MarketOutcome outcome = market.create(new ListingRequest(
                characterId,
                command.getInventoryItemId(),
                command.getQuantity(),
                command.getPrice(),
                command.getIdempotencyKey()));
        if (outcome == MarketOutcome.OK) {
            alertWatchers(characterId);
        }
        return CreateListingResponse.newBuilder()
                .setOutcome(RESULTS.get(outcome))
                .build();
    }

    BuyListingResponse buy(long characterId, BuyListingCommand command) {
        MarketUseCase.BuyResult result = market.buy(characterId, command.getListingId(), command.getIdempotencyKey());
        result.sale().ifPresent(this::notifySeller);
        return BuyListingResponse.newBuilder()
                .setOutcome(RESULTS.get(result.outcome()))
                .build();
    }

    CancelListingResponse cancel(long characterId, CancelListingCommand command) {
        return CancelListingResponse.newBuilder()
                .setOutcome(RESULTS.get(market.cancel(characterId, command.getListingId())))
                .build();
    }

    /**
     * ADR-196 — món vừa bày (dòng mới nhất của người bán) khớp ai đang theo dõi thì báo người đó nếu đang online. Người
     * bán không tự nhận báo về món của mình.
     */
    private void alertWatchers(long sellerId) {
        market.mine(sellerId).stream()
                .max(java.util.Comparator.comparingLong(MarketListing::id))
                .ifPresent(listing -> {
                    byte[] payload = dev.kage.protocol.MarketWatchAlertEvent.newBuilder()
                            .setListingId(listing.id())
                            .setItemTemplateId(listing.itemTemplateId())
                            .setQuantity(listing.quantity())
                            .setPrice(listing.price())
                            .setUnitPrice(dev.kage.market.api.MarketWatchFacade.unitPrice(
                                    listing.price(), listing.quantity()))
                            .setSellerName(listing.sellerName())
                            .build()
                            .toByteArray();
                    for (long watcher :
                            watch.watchersFor(listing.itemTemplateId(), listing.price(), listing.quantity())) {
                        if (watcher != sellerId) {
                            registry.sendToCharacterIfOnline(
                                    watcher, Opcode.OPCODE_MARKET_WATCH_ALERT_EVENT_VALUE, 0, payload);
                        }
                    }
                });
    }

    /** Người bán đang online thì biết ngay; vàng mới tới riêng bằng GoldChangedEvent. */
    private void notifySeller(MarketUseCase.Sale sale) {
        registry.sendToCharacterIfOnline(
                sale.sellerId(),
                Opcode.OPCODE_LISTING_SOLD_EVENT_VALUE,
                0,
                ListingSoldEvent.newBuilder()
                        .setListingId(sale.listingId())
                        .setItemTemplateId(sale.itemTemplateId())
                        .setQuantity(sale.quantity())
                        .setProceeds(sale.proceeds())
                        .build()
                        .toByteArray());
    }

    private static MarketListingView view(MarketListing listing) {
        return MarketListingView.newBuilder()
                .setListingId(listing.id())
                .setSellerId(listing.sellerId())
                .setSellerName(listing.sellerName())
                .setItemTemplateId(listing.itemTemplateId())
                .setQuantity(listing.quantity())
                .setPrice(listing.price())
                .setExpiresAtEpochSeconds(listing.expiresAt().getEpochSecond())
                .build();
    }
}
