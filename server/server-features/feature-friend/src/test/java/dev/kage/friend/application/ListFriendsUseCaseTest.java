package dev.kage.friend.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;
import dev.kage.friend.domain.FriendshipStatus;

class ListFriendsUseCaseTest {

    @Test
    void shouldReturnOtherPartyIds_regardlessOfRequesterDirection() {
        FakeFriendPort port = new FakeFriendPort();
        port.friendships.add(new Friendship(1, 5, 7, FriendshipStatus.ACCEPTED, Instant.now(), Instant.now()));
        port.friendships.add(new Friendship(2, 9, 5, FriendshipStatus.ACCEPTED, Instant.now(), Instant.now()));
        ListFriendsUseCase useCase = new ListFriendsUseCase(port);

        List<Long> result = useCase.execute(5);

        assertThat(result).containsExactlyInAnyOrder(7L, 9L);
    }

    @Test
    void shouldReturnEmpty_whenNoFriends() {
        ListFriendsUseCase useCase = new ListFriendsUseCase(new FakeFriendPort());

        assertThat(useCase.execute(5)).isEmpty();
    }

    static final class FakeFriendPort implements FriendPort {
        private final List<Friendship> friendships = new ArrayList<>();

        @Override
        public Optional<Friendship> findBetween(long characterIdA, long characterIdB) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public Friendship sendRequest(long requesterId, long addresseeId) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public boolean accept(long friendshipId, long addresseeId) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public boolean reject(long friendshipId, long addresseeId) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public boolean remove(long characterId, long friendCharacterId) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public List<Friendship> listAccepted(long characterId) {
            return friendships.stream().filter(f -> f.involves(characterId)).toList();
        }

        @Override
        public List<Friendship> listPendingIncoming(long characterId) {
            throw new UnsupportedOperationException("không dùng ở test này");
        }
    }
}
