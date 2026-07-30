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

class ListPendingFriendRequestsUseCaseTest {

    @Test
    void shouldReturnOnlyRequestsAddressedToCaller() {
        FakeFriendPort port = new FakeFriendPort();
        port.friendships.add(new Friendship(1, 7, 5, FriendshipStatus.PENDING, Instant.now(), null));
        ListPendingFriendRequestsUseCase useCase = new ListPendingFriendRequestsUseCase(port);

        List<Friendship> result = useCase.execute(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).requesterId()).isEqualTo(7);
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
            throw new UnsupportedOperationException("không dùng ở test này");
        }

        @Override
        public List<Friendship> listPendingIncoming(long characterId) {
            return friendships.stream()
                    .filter(f -> f.addresseeId() == characterId && f.status() == FriendshipStatus.PENDING)
                    .toList();
        }
    }
}
