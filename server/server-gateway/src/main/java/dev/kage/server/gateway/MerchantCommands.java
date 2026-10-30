package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.SplittableRandom;

import dev.kage.protocol.BuyFromMerchantResponse;
import dev.kage.protocol.GetTravelingMerchantResponse;
import dev.kage.protocol.MerchantBuyResult;
import dev.kage.protocol.MerchantStockView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.TravelingMerchantArrivedEvent;
import dev.kage.shop.api.TravelingMerchantFacade;
import dev.kage.shop.domain.MerchantBuyOutcome;
import dev.kage.shop.domain.MerchantStock;
import dev.kage.shop.domain.MerchantVisit;
import dev.kage.world.api.WaypointFacade;
import dev.kage.world.domain.Waypoint;

/**
 * Thương nhân du hành (ADR-118): xem hàng (ở đâu cũng được), mua (phải đứng gần trạm — kiểm ở ĐÂY bằng vị trí server),
 * và tác vụ nền lên lịch + báo cả server khi thương nhân tới. Chạy trên Virtual Thread / bộ hẹn giờ lô 23.
 */
final class MerchantCommands {

    /** Bán kính quanh trạm được mua. */
    static final float BUY_RADIUS_METERS = 8f;

    private static final long JOB_PERIOD_SECONDS = 60L;

    private final TravelingMerchantFacade merchant;
    private final WaypointFacade waypoints;
    private final NpcProximity proximity;
    private final Clock clock;

    MerchantCommands(TravelingMerchantFacade merchant, WaypointFacade waypoints, NpcProximity proximity, Clock clock) {
        this.merchant = merchant;
        this.waypoints = waypoints;
        this.proximity = proximity;
        this.clock = clock;
    }

    GetTravelingMerchantResponse status() {
        Optional<MerchantVisit> active = merchant.active(clock.instant());
        GetTravelingMerchantResponse.Builder response = GetTravelingMerchantResponse.newBuilder();
        active.ifPresent(visit -> {
            Optional<Waypoint> station = waypoints.find(visit.waypointCode());
            response.setPresent(true)
                    .setWaypointCode(visit.waypointCode())
                    .setWaypointNameKey(station.map(Waypoint::nameKey).orElse(""))
                    .setX(station.map(Waypoint::x).orElse(0f))
                    .setEndsAtEpochMs(visit.endsAt().toEpochMilli());
            for (MerchantStock line : visit.stock()) {
                response.addStock(MerchantStockView.newBuilder()
                        .setItemTemplateId(line.itemTemplateId())
                        .setPrice(line.price())
                        .setRemaining(line.remaining()));
            }
        });
        return response.build();
    }

    BuyFromMerchantResponse buy(long characterId, long itemTemplateId) {
        Instant now = clock.instant();
        Optional<MerchantVisit> active = merchant.active(now);
        Optional<Waypoint> station = active.flatMap(visit -> waypoints.find(visit.waypointCode()));
        if (active.isEmpty()
                || station.isEmpty()
                || !proximity.isNear(
                        characterId, station.get().x(), station.get().y(), BUY_RADIUS_METERS)) {
            return result(MerchantBuyOutcome.NOT_PRESENT);
        }
        return result(merchant.buy(characterId, active.get().id(), itemTemplateId, now));
    }

    private static BuyFromMerchantResponse result(MerchantBuyOutcome outcome) {
        return BuyFromMerchantResponse.newBuilder()
                .setResult(MerchantBuyResult.valueOf(
                        "MERCHANT_BUY_RESULT_" + outcome.name().toUpperCase(Locale.ROOT)))
                .build();
    }

    /** Tác vụ nền: lên lịch hôm nay nếu chưa có, báo cả server mỗi lần ghé vừa bắt đầu (đúng một lần). */
    static ExpansionJobs.Job job(
            TravelingMerchantFacade merchant,
            WaypointFacade waypoints,
            ConnectedCharacterRegistry registry,
            Clock clock) {
        SplittableRandom random = new SplittableRandom();
        return new ExpansionJobs.Job("thương nhân du hành", JOB_PERIOD_SECONDS, () -> {
            Instant now = clock.instant();
            List<String> stations =
                    waypoints.waypoints().stream().map(Waypoint::code).toList();
            merchant.ensurePlanned(now, stations, random);
            for (MerchantVisit visit : merchant.claimArrivals(now)) {
                Optional<Waypoint> station = waypoints.find(visit.waypointCode());
                registry.broadcastToAll(
                        Opcode.OPCODE_TRAVELING_MERCHANT_ARRIVED_EVENT_VALUE,
                        TravelingMerchantArrivedEvent.newBuilder()
                                .setWaypointCode(visit.waypointCode())
                                .setWaypointNameKey(
                                        station.map(Waypoint::nameKey).orElse(""))
                                .setX(station.map(Waypoint::x).orElse(0f))
                                .setEndsAtEpochMs(visit.endsAt().toEpochMilli())
                                .build()
                                .toByteArray());
            }
        });
    }
}
