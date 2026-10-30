package dev.kage.shop.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MerchantPlannerTest {

    private static final Instant DAY = Instant.parse("2026-10-29T17:00:00Z"); // 00:00 30/10 giờ VN
    private static final List<String> STATIONS = List.of("sect_gate", "spirit_vein", "east_ridge");
    private static final List<MerchantOffer> OFFERS = List.of(
            new MerchantOffer(1L, 100L, 5, 10),
            new MerchantOffer(2L, 200L, 3, 5),
            new MerchantOffer(3L, 300L, 2, 1),
            new MerchantOffer(4L, 400L, 1, 1));

    @Test
    @DisplayName("Mỗi ngày 2–3 lần ghé, không chồng giờ, trong khung 8h–22h30 giờ VN, mỗi lần 3 món khác nhau")
    void plansTwoOrThreeNonOverlappingVisits() {
        for (long seed = 0; seed < 200; seed++) {
            List<MerchantPlanner.PlannedVisit> plan =
                    MerchantPlanner.planDay(DAY, STATIONS, OFFERS, new SplittableRandom(seed));

            assertThat(plan).hasSizeBetween(MerchantPlanner.MIN_VISITS, MerchantPlanner.MAX_VISITS);
            for (int i = 0; i < plan.size(); i++) {
                MerchantPlanner.PlannedVisit visit = plan.get(i);
                assertThat(visit.startsAt())
                        .isBetween(DAY.plus(Duration.ofHours(8)), DAY.plus(Duration.ofMinutes(22 * 60 + 30)));
                assertThat(STATIONS).contains(visit.waypointCode());
                assertThat(visit.stock()).hasSize(MerchantPlanner.LINES_PER_VISIT);
                assertThat(visit.stock().stream()
                                .map(MerchantStock::itemTemplateId)
                                .distinct())
                        .hasSize(3);
                if (i > 0) {
                    assertThat(visit.startsAt())
                            .isAfterOrEqualTo(plan.get(i - 1).endsAt());
                }
            }
        }
    }

    @Test
    @DisplayName("Không trạm hoặc không hàng ⇒ không lên lịch")
    void nothingToPlanWithoutStationsOrOffers() {
        assertThat(MerchantPlanner.planDay(DAY, List.of(), OFFERS, new SplittableRandom(1)))
                .isEmpty();
        assertThat(MerchantPlanner.planDay(DAY, STATIONS, List.of(), new SplittableRandom(1)))
                .isEmpty();
    }
}
