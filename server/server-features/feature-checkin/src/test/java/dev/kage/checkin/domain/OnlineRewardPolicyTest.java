package dev.kage.checkin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** ADR-096 — giây của phiên tính cho ngày của mốc kết thúc (giờ Việt Nam). */
class OnlineRewardPolicyTest {

    @Test
    void shouldCountOnlyThePartAfterVietnamMidnight() {
        // 23:50 → 00:20 giờ Việt Nam (UTC+7) ⇒ chỉ 20 phút thuộc ngày mới.
        Instant entered = Instant.parse("2026-10-24T16:50:00Z");
        Instant left = Instant.parse("2026-10-24T17:20:00Z");
        assertThat(OnlineRewardPolicy.secondsOnDayOf(entered, left)).isEqualTo(20 * 60);
        assertThat(OnlineRewardPolicy.dayOf(left)).hasToString("2026-10-25");
    }

    @Test
    void shouldCountTheWholeSessionWithinOneDay() {
        Instant entered = Instant.parse("2026-10-24T03:00:00Z");
        assertThat(OnlineRewardPolicy.secondsOnDayOf(entered, entered.plusSeconds(90)))
                .isEqualTo(90);
        assertThat(OnlineRewardPolicy.milestone(30)).isPresent();
        assertThat(OnlineRewardPolicy.milestone(15)).isEmpty();
    }
}
