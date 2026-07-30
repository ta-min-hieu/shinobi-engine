package dev.kage.friend.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import dev.kage.friend.domain.DuplicateFriendRequestException;
import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;
import dev.kage.friend.domain.FriendshipStatus;

class SendFriendRequestUseCaseTest {

    @Test
    void shouldReject_whenRequestingSelf() {
        SendFriendRequestUseCase useCase = new SendFriendRequestUseCase(new FakeFriendPort());

        SendFriendRequestUseCase.Result result = useCase.execute(5, 5);

        assertThat(result).isEqualTo(SendFriendRequestUseCase.Result.CANNOT_FRIEND_SELF);
    }

    @Test
    void shouldReject_whenAlreadyFriends() {
        FakeFriendPort port = new FakeFriendPort();
        port.friendships.add(new Friendship(1, 5, 7, FriendshipStatus.ACCEPTED, Instant.now(), Instant.now()));
        SendFriendRequestUseCase useCase = new SendFriendRequestUseCase(port);

        SendFriendRequestUseCase.Result result = useCase.execute(5, 7);

        assertThat(result).isEqualTo(SendFriendRequestUseCase.Result.ALREADY_FRIENDS);
    }

    @Test
    void shouldReject_whenRequestAlreadyPending() {
        FakeFriendPort port = new FakeFriendPort();
        port.friendships.add(new Friendship(1, 5, 7, FriendshipStatus.PENDING, Instant.now(), null));
        SendFriendRequestUseCase useCase = new SendFriendRequestUseCase(port);

        SendFriendRequestUseCase.Result result = useCase.execute(5, 7);

        assertThat(result).isEqualTo(SendFriendRequestUseCase.Result.REQUEST_ALREADY_PENDING);
    }

    @Test
    void shouldCreatePendingRequest_whenNoExistingRelationship() {
        FakeFriendPort port = new FakeFriendPort();
        SendFriendRequestUseCase useCase = new SendFriendRequestUseCase(port);

        SendFriendRequestUseCase.Result result = useCase.execute(5, 7);

        assertThat(result).isEqualTo(SendFriendRequestUseCase.Result.OK);
        assertThat(port.friendships).hasSize(1);
        assertThat(port.friendships.get(0).status()).isEqualTo(FriendshipStatus.PENDING);
    }

    /** Fake = implementation in-memory THẬT (TESTING.md §4), không Mockito. */
    static final class FakeFriendPort implements FriendPort {
        private final List<Friendship> friendships = new ArrayList<>();
        private final AtomicLong nextId = new AtomicLong(1);

        @Override
        public Optional<Friendship> findBetween(long characterIdA, long characterIdB) {
            return friendships.stream()
                    .filter(f -> f.involves(characterIdA) && f.involves(characterIdB))
                    .findFirst();
        }

        @Override
        public Friendship sendRequest(long requesterId, long addresseeId) {
            if (findBetween(requesterId, addresseeId).isPresent()) {
                throw new DuplicateFriendRequestException(requesterId, addresseeId);
            }
            Friendship friendship = new Friendship(
                    nextId.getAndIncrement(), requesterId, addresseeId, FriendshipStatus.PENDING, Instant.now(), null);
            friendships.add(friendship);
            return friendship;
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
            throw new UnsupportedOperationException("không dùng ở test này");
        }
    }
}
