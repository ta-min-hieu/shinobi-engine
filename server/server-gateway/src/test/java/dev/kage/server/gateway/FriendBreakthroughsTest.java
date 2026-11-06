package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** ADR-182 — chúc mừng một lần mỗi lần đột phá, trong 30 phút. */
final class FriendBreakthroughsTest {

    private static final Instant AT = Instant.parse("2026-11-06T12:00:00Z");
    private static final long HERO = 1L;
    private static final long FRIEND = 2L;
    private static final long OTHER = 3L;

    @Test
    void oncePerFriendPerBreakthrough() {
        FriendBreakthroughs breakthroughs = new FriendBreakthroughs();
        assertThat(breakthroughs.congratulate(FRIEND, HERO, AT).outcome())
                .isEqualTo(FriendBreakthroughs.Outcome.NO_RECENT_BREAKTHROUGH);
        breakthroughs.record(HERO, 3, AT);
        FriendBreakthroughs.Congratulation first = breakthroughs.congratulate(FRIEND, HERO, AT.plusSeconds(5));
        assertThat(first.outcome()).isEqualTo(FriendBreakthroughs.Outcome.OK);
        assertThat(first.realm()).isEqualTo(3);
        assertThat(breakthroughs.congratulate(FRIEND, HERO, AT.plusSeconds(6)).outcome())
                .isEqualTo(FriendBreakthroughs.Outcome.ALREADY_CONGRATULATED);
        assertThat(breakthroughs.congratulate(OTHER, HERO, AT.plusSeconds(7)).outcome())
                .isEqualTo(FriendBreakthroughs.Outcome.OK);
        breakthroughs.record(HERO, 4, AT.plusSeconds(10)); // lần đột phá mới ⇒ chúc lại được
        assertThat(breakthroughs.congratulate(FRIEND, HERO, AT.plusSeconds(11)).outcome())
                .isEqualTo(FriendBreakthroughs.Outcome.OK);
    }

    @Test
    void windowClosesAfterThirtyMinutes() {
        FriendBreakthroughs breakthroughs = new FriendBreakthroughs();
        breakthroughs.record(HERO, 1, AT);
        Instant late = AT.plus(FriendBreakthroughs.WINDOW).plus(Duration.ofSeconds(1));
        assertThat(breakthroughs.congratulate(FRIEND, HERO, late).outcome())
                .isEqualTo(FriendBreakthroughs.Outcome.NO_RECENT_BREAKTHROUGH);
    }
}
