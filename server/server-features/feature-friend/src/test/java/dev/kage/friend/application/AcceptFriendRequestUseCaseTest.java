package dev.kage.friend.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.friend.domain.FriendListLimit;
import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;
import dev.kage.friend.domain.FriendshipStatus;

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

    /** Trần bạn bè (quyết định chủ dự án 2026-10-11) kiểm ở cả hai phía của một lần chấp nhận. */
    @Test
    void shouldRefuse_whenTheAcceptersOwnListIsFull() {
        FakeFriendPort port = new FakeFriendPort();
        port.friendships.add(new Friendship(1, 5, 7, FriendshipStatus.PENDING, Instant.now(), null));
        port.giveFriends(7, FriendListLimit.MAX_FRIENDS);
        AcceptFriendRequestUseCase useCase = new AcceptFriendRequestUseCase(port);

        AcceptFriendRequestUseCase.Result result = useCase.execute(1, 7);

        assertThat(result).isEqualTo(AcceptFriendRequestUseCase.Result.FRIEND_LIST_FULL);
        assertThat(port.friendships.get(0).status())
                .as("lời mời còn nguyên, chấp nhận lại được khi có chỗ")
                .isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    void shouldRefuse_whenTheRequestersListIsFull() {
        FakeFriendPort port = new FakeFriendPort();
        port.friendships.add(new Friendship(1, 5, 7, FriendshipStatus.PENDING, Instant.now(), null));
        port.giveFriends(5, FriendListLimit.MAX_FRIENDS);
        AcceptFriendRequestUseCase useCase = new AcceptFriendRequestUseCase(port);

        AcceptFriendRequestUseCase.Result result = useCase.execute(1, 7);

        assertThat(result).isEqualTo(AcceptFriendRequestUseCase.Result.REQUESTER_FRIEND_LIST_FULL);
        assertThat(port.friendships.get(0).status()).isEqualTo(FriendshipStatus.PENDING);
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
                    friendships.set(
                            i,
                            new Friendship(
                                    f.id(),
                                    f.requesterId(),
                                    f.addresseeId(),
                                    FriendshipStatus.ACCEPTED,
                                    f.createdAt(),
                                    Instant.now()));
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
            return friendships.stream()
                    .filter(f -> f.involves(characterId) && f.status() == FriendshipStatus.ACCEPTED)
                    .toList();
        }

        @Override
        public List<Friendship> listPendingIncoming(long characterId) {
            return friendships.stream()
                    .filter(f -> f.addresseeId() == characterId && f.status() == FriendshipStatus.PENDING)
                    .toList();
        }

        /** Cho {@code characterId} đủ {@code count} bạn đã chấp nhận, id bạn bắt đầu từ 1000. */
        void giveFriends(long characterId, int count) {
            for (int i = 0; i < count; i++) {
                friendships.add(new Friendship(
                        10_000L + friendships.size(),
                        characterId,
                        1000L + i,
                        FriendshipStatus.ACCEPTED,
                        Instant.now(),
                        Instant.now()));
            }
        }
    }
}
