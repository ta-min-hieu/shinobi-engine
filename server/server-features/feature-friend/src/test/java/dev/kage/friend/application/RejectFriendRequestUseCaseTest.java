package dev.kage.friend.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;
import dev.kage.friend.domain.FriendshipStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RejectFriendRequestUseCaseTest {

    @Test
    void shouldDeletePendingRequest_whenAddressedToCaller() {
        FakeFriendPort port = new FakeFriendPort();
        port.friendships.add(new Friendship(1, 5, 7, FriendshipStatus.PENDING, Instant.now(), null));
        RejectFriendRequestUseCase useCase = new RejectFriendRequestUseCase(port);

        RejectFriendRequestUseCase.Result result = useCase.execute(1, 7);

        assertThat(result).isEqualTo(RejectFriendRequestUseCase.Result.OK);
        assertThat(port.friendships).isEmpty();
    }

    @Test
    void shouldReject_whenFriendshipDoesNotExist() {
        RejectFriendRequestUseCase useCase = new RejectFriendRequestUseCase(new FakeFriendPort());

        RejectFriendRequestUseCase.Result result = useCase.execute(999, 7);

        assertThat(result).isEqualTo(RejectFriendRequestUseCase.Result.NOT_FOUND);
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
            return friendships.removeIf(f -> f.id() == friendshipId
                    && f.addresseeId() == addresseeId
                    && f.status() == FriendshipStatus.PENDING);
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
            throw new UnsupportedOperationException("không dùng ở test này");
        }
    }
}
