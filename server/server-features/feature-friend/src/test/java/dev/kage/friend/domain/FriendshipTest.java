package dev.kage.friend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class FriendshipTest {

    @Test
    void shouldRejectSelfFriendship() {
        assertThatThrownBy(() -> new Friendship(1, 5, 5, FriendshipStatus.PENDING, Instant.now(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void otherPartyShouldReturnAddressee_whenCalledByRequester() {
        var friendship = new Friendship(1, 5, 7, FriendshipStatus.ACCEPTED, Instant.now(), Instant.now());

        assertThat(friendship.otherParty(5)).isEqualTo(7);
    }

    @Test
    void otherPartyShouldReturnRequester_whenCalledByAddressee() {
        var friendship = new Friendship(1, 5, 7, FriendshipStatus.ACCEPTED, Instant.now(), Instant.now());

        assertThat(friendship.otherParty(7)).isEqualTo(5);
    }

    @Test
    void otherPartyShouldReject_whenCharacterNotInvolved() {
        var friendship = new Friendship(1, 5, 7, FriendshipStatus.ACCEPTED, Instant.now(), Instant.now());

        assertThatThrownBy(() -> friendship.otherParty(99)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void involvesShouldMatchEitherSide() {
        var friendship = new Friendship(1, 5, 7, FriendshipStatus.ACCEPTED, Instant.now(), Instant.now());

        assertThat(friendship.involves(5)).isTrue();
        assertThat(friendship.involves(7)).isTrue();
        assertThat(friendship.involves(99)).isFalse();
    }
}
