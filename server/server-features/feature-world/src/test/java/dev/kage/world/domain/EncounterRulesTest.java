package dev.kage.world.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

/** ADR-121 — xác suất 2%, ngày theo giờ Việt Nam, chọn theo trọng số. */
class EncounterRulesTest {

    @Test
    void shouldTriggerAboutTwoPercentOfKills() {
        SplittableRandom random = new SplittableRandom(7);
        int hits = 0;
        int kills = 100_000;
        for (int i = 0; i < kills; i++) {
            if (EncounterRules.triggers(random)) {
                hits++;
            }
        }
        assertThat(hits).isBetween(1_800, 2_200);
    }

    @Test
    void dayShouldStartAtMidnightVietnamTime() {
        // 23:30 UTC ngày 30 = 06:30 sáng 31 giờ VN ⇒ ngày bắt đầu 17:00 UTC ngày 30.
        assertThat(EncounterRules.dayStart(Instant.parse("2026-10-30T23:30:00Z")))
                .isEqualTo(Instant.parse("2026-10-30T17:00:00Z"));
        // 16:59 UTC ngày 30 = 23:59 ngày 30 giờ VN ⇒ ngày bắt đầu 17:00 UTC ngày 29.
        assertThat(EncounterRules.dayStart(Instant.parse("2026-10-30T16:59:00Z")))
                .isEqualTo(Instant.parse("2026-10-29T17:00:00Z"));
    }

    @Test
    void pickWeightedShouldFollowTheWeights() {
        SplittableRandom random = new SplittableRandom(11);
        int[] counts = new int[3];
        for (int i = 0; i < 60_000; i++) {
            counts[EncounterRules.pickWeighted(new int[] {1, 2, 3}, random)]++;
        }
        assertThat(counts[0]).isBetween(9_000, 11_000);
        assertThat(counts[1]).isBetween(19_000, 21_000);
        assertThat(counts[2]).isBetween(29_000, 31_000);
        assertThat(EncounterRules.pickWeighted(new int[] {5}, random)).isZero();
    }
}
