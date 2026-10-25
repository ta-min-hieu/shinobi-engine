package dev.kage.checkin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** ADR-106 — điểm mỗi việc kẹp ở trần số lần; tổng cộng mọi việc. */
class ActivityRulesTest {

    private static final ActivityPort.Definition BOUNTY =
            new ActivityPort.Definition("bounty", "activity.bounty", 10, 5);
    private static final ActivityPort.Definition HERB =
            new ActivityPort.Definition("herb_gather", "activity.herb_gather", 5, 4);

    @Test
    void shouldCapEachActivityAtItsMaxCount() {
        assertThat(ActivityRules.pointsOf(BOUNTY, 0)).isZero();
        assertThat(ActivityRules.pointsOf(BOUNTY, 3)).isEqualTo(30);
        assertThat(ActivityRules.pointsOf(BOUNTY, 9)).isEqualTo(50);
        assertThat(ActivityRules.pointsOf(BOUNTY, -2)).isZero();
    }

    @Test
    void shouldSumAllActivitiesAndIgnoreUnknownCodes() {
        int total = ActivityRules.totalPoints(
                List.of(BOUNTY, HERB), Map.of("bounty", 2, "herb_gather", 10, "something_else", 7));
        assertThat(total).isEqualTo(20 + 20);
    }
}
