package dev.kage.friend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-084 — mốc thân mật chạm đúng một lần, khi đi qua nó. */
class FriendGiftPolicyTest {

    @Test
    void shouldReportOnlyTheMilestoneJustCrossed() {
        assertThat(FriendGiftPolicy.milestoneCrossed(90, 100)).contains(100);
        assertThat(FriendGiftPolicy.milestoneCrossed(100, 110)).isEmpty();
        assertThat(FriendGiftPolicy.milestoneCrossed(290, 300)).contains(300);
        assertThat(FriendGiftPolicy.milestoneCrossed(0, 10)).isEmpty();
    }
}
