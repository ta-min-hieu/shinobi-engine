package dev.kage.shop.domain;

import java.time.Instant;
import java.util.List;

/** Một lần thương nhân du hành ghé một trạm (ADR-118). */
public record MerchantVisit(long id, String waypointCode, Instant startsAt, Instant endsAt, List<MerchantStock> stock) {

    public MerchantVisit {
        stock = List.copyOf(stock);
    }
}
