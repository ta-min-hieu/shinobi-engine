package dev.kage.guild.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** ADR-090 — khung giờ hằng tuần theo UTC; bang đông nhất (không hoà) chiếm vùng. */
class SpiritVeinRulesTest {

    // Thứ Bảy 23:40 UTC, 30 phút — khung vắt qua nửa đêm sang Chủ nhật.
    private static final SpiritVein.Schedule SATURDAY_LATE = new SpiritVein.Schedule(6, 23 * 60 + 40, 30);
    private static final Instant START = Instant.parse("2026-10-24T23:40:00Z");

    @Test
    void shouldOpenOnlyInsideTheWindow_evenAcrossMidnight() {
        assertThat(SpiritVeinRules.activeWindowStart(SATURDAY_LATE, Instant.parse("2026-10-24T23:39:59Z")))
                .isEmpty();
        assertThat(SpiritVeinRules.activeWindowStart(SATURDAY_LATE, START)).contains(START);
        assertThat(SpiritVeinRules.activeWindowStart(SATURDAY_LATE, Instant.parse("2026-10-25T00:09:59Z")))
                .contains(START);
        assertThat(SpiritVeinRules.activeWindowStart(SATURDAY_LATE, Instant.parse("2026-10-25T00:10:00Z")))
                .isEmpty();
    }

    @Test
    void shouldFindTheLastClosedAndTheNextWindow() {
        Instant justAfter = Instant.parse("2026-10-25T00:10:00Z");
        assertThat(SpiritVeinRules.lastClosedWindowStart(SATURDAY_LATE, justAfter))
                .contains(START);
        assertThat(SpiritVeinRules.lastClosedWindowStart(SATURDAY_LATE, START))
                .contains(Instant.parse("2026-10-17T23:40:00Z"));
        assertThat(SpiritVeinRules.nextWindowStart(SATURDAY_LATE, START))
                .isEqualTo(Instant.parse("2026-10-31T23:40:00Z"));
        assertThat(SpiritVeinRules.nextWindowStart(SATURDAY_LATE, Instant.parse("2026-10-20T00:00:00Z")))
                .isEqualTo(START);
    }

    @Test
    void shouldGiveTheZoneToTheLargestGroup_andNobodyOnATie() {
        assertThat(SpiritVeinRules.occupier(Map.of(1L, 2, 2L, 1))).contains(1L);
        assertThat(SpiritVeinRules.occupier(Map.of(1L, 2, 2L, 2))).isEmpty();
        assertThat(SpiritVeinRules.occupier(Map.of(1L, 1, 2L, 3, 3L, 3))).isEmpty();
        assertThat(SpiritVeinRules.occupier(Map.of())).isEmpty();
    }
}
