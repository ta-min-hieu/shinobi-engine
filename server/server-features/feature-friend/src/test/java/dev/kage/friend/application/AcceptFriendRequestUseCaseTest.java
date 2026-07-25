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

class AcceptFriendRequestUseCaseTest {

    @Test
    void shouldAccept_whenPendingRequestAddressedToCaller() {
        FakeFriendPort port = new FakeFriendPort();
        port.friendships.add(new Friendship(1, 5, 7, FriendshipStatus.PENDING, Instant.now(), null));
        AcceptFriendRequestUseCase useCase = new AcceptFriendRequestUseCase(port);

        AcceptFriendRequestUseCase.Result result = useCase.execute(1, 7);

        assertThat(result).isEqualTo(AcceptFriendRequestUseCase.Result.OK);
        assertThat(port.friendships.get(0).status()).isEqualTo(FriendshipStatus.ACCEPTED);
    }

    @Test
    void shouldReject_whenCallerIsNotTheAddressee() {
        FakeFriendPort port = new FakeFriendPort();
        port.friendships.add(new Friendship(1, 5, 7, FriendshipStatus.PENDING, Instant.now(), null));
        AcceptFriendRequestUseCase useCase = new AcceptFriendRequestUseCase(port);

        AcceptFriendRequestUseCase.Result result = useCase.execute(1, 5);

        assertThat(result).isEqualTo(AcceptFriendRequestUseCase.Result.NOT_FOUND);
    }

    @Test
    void shouldReject_whenFriendshipDoesNotExist() {
        AcceptFriendRequestUseCase useCase = new AcceptFriendRequestUseCase(new FakeFriendPort());

        AcceptFriendRequestUseCase.Result result = useCase.execute(999, 7);

        assertThat(result).isEqualTo(AcceptFriendRequestUseCase.Result.NOT_FOUND);
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
            for (int i = 0; i < friendships.size(); i++) {
                Friendship f = friendships.get(i);
                boolean matches = f.id() == friendshipId
                        && f.addresseeId() == addresseeId
                        && f.status() == FriendshipStatus.PENDING;
                if (matches) {
                    friendships.set(i, new Friendship(f.id(), f.requesterId(), f.addresseeId(),
                            FriendshipStatus.ACCEPTED, f.createdAt(), Instant.now()));
                    return true;
                }
            }
            return false;
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
