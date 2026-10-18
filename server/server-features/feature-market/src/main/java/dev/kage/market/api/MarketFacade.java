package dev.kage.market.api;

import java.util.List;
import java.util.Optional;

import dev.kage.market.application.MarketUseCase;
import dev.kage.market.domain.ListingRequest;
import dev.kage.market.domain.MarketListing;
import dev.kage.market.domain.MarketOutcome;

/** API của {@code feature-market} (ADR-063). Mọi hàm chạm DB — chỉ gọi trên Virtual Thread. */
public final class MarketFacade {

    /** Composition không có phường thị (test) — không dòng nào, mọi lệnh {@code NOT_FOUND}. */
    public static final MarketFacade DISABLED = new MarketFacade(Optional.empty());

    private final Optional<MarketUseCase> useCase;

    /** Dựng ở composition root. */
    public MarketFacade(MarketUseCase useCase) {
        this(Optional.of(useCase));
    }

    private MarketFacade(Optional<MarketUseCase> useCase) {
        this.useCase = useCase;
    }

    /** Treo bán — xem {@link MarketUseCase#create}. */
    public MarketOutcome create(ListingRequest request) {
        return useCase.map(u -> u.create(request)).orElse(MarketOutcome.NOT_FOUND);
    }

    /** Mua — xem {@link MarketUseCase#buy}. */
    public MarketUseCase.BuyResult buy(long buyerId, long listingId, String key) {
        return useCase.map(u -> u.buy(buyerId, listingId, key))
                .orElseGet(() -> new MarketUseCase.BuyResult(MarketOutcome.NOT_FOUND, Optional.empty()));
    }

    /** Huỷ — xem {@link MarketUseCase#cancel}. */
    public MarketOutcome cancel(long sellerId, long listingId) {
        return useCase.map(u -> u.cancel(sellerId, listingId)).orElse(MarketOutcome.NOT_FOUND);
    }

    /** Dòng đang bán, mới nhất trước. */
    public List<MarketListing> browse(Optional<Long> itemTemplateId) {
        return useCase.map(u -> u.browse(itemTemplateId)).orElse(List.of());
    }

    /** Dòng đang bán của chính người hỏi. */
    public List<MarketListing> mine(long sellerId) {
        return useCase.map(u -> u.mine(sellerId)).orElse(List.of());
    }

    /** Bước (1) của hết hạn — xem {@link MarketUseCase#expireAndListUnreturned}. */
    public List<MarketListing> expireAndListUnreturned(int limit) {
        return useCase.map(u -> u.expireAndListUnreturned(limit)).orElse(List.of());
    }

    /** Đã gửi thư trả món của dòng hết hạn. */
    public void markReturned(long listingId) {
        useCase.ifPresent(u -> u.markReturned(listingId));
    }
}
