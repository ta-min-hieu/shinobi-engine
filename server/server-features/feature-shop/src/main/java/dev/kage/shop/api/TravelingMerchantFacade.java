package dev.kage.shop.api;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import dev.kage.shop.domain.MerchantBuyOutcome;
import dev.kage.shop.domain.MerchantPlanner;
import dev.kage.shop.domain.MerchantPort;
import dev.kage.shop.domain.MerchantVisit;

/** Thương nhân du hành (ADR-118). Chạm DB — chỉ gọi trên Virtual Thread / tác vụ nền. */
public final class TravelingMerchantFacade {

    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Composition không có thương nhân (test). */
    public static final TravelingMerchantFacade DISABLED = new TravelingMerchantFacade(Optional.empty());

    private final Optional<MerchantPort> port;

    /** Dựng ở composition root. */
    public TravelingMerchantFacade(MerchantPort port) {
        this(Optional.of(port));
    }

    private TravelingMerchantFacade(Optional<MerchantPort> port) {
        this.port = port;
    }

    /** Hôm nay (giờ VN) chưa có lịch thì lên lịch. Gọi định kỳ; gọi nhiều lần vô hại. */
    public void ensurePlanned(Instant now, List<String> stations, RandomGenerator random) {
        port.ifPresent(p -> {
            Instant dayStart = ZonedDateTime.ofInstant(now, VIETNAM)
                    .toLocalDate()
                    .atStartOfDay(VIETNAM)
                    .toInstant();
            if (p.countVisitsStartingBetween(dayStart, dayStart.plus(java.time.Duration.ofDays(1))) > 0) {
                return;
            }
            for (MerchantPlanner.PlannedVisit visit : MerchantPlanner.planDay(dayStart, stations, p.offers(), random)) {
                p.insertVisit(visit.waypointCode(), visit.startsAt(), visit.endsAt(), visit.stock());
            }
        });
    }

    /** Lần ghé đang diễn ra (kèm kho còn lại), rỗng nếu thương nhân không ở đâu cả. */
    public Optional<MerchantVisit> active(Instant now) {
        return port.flatMap(p -> p.active(now));
    }

    /** Lần ghé vừa bắt đầu mà chưa báo — để báo cả server đúng một lần. */
    public List<MerchantVisit> claimArrivals(Instant now) {
        return port.map(p -> p.claimUnannouncedActive(now)).orElse(List.of());
    }

    /** Mua một cái — {@code characterId} từ session; người gọi đã kiểm đứng gần trạm. */
    public MerchantBuyOutcome buy(long characterId, long visitId, long itemTemplateId, Instant now) {
        return port.map(p -> p.buy(characterId, visitId, itemTemplateId, now)).orElse(MerchantBuyOutcome.NOT_PRESENT);
    }
}
