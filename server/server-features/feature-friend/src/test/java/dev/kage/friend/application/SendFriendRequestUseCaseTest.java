package dev.kage.friend.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import dev.kage.friend.domain.DuplicateFriendRequestException;
import dev.kage.friend.domain.FriendListLimit;
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

    /** Trần bạn bè (quyết định chủ dự án 2026-10-11): danh sách của người GỬI đã đầy thì không mời thêm. */
    @Test
    void shouldRefuse_whenTheSendersFriendListIsFull() {
        FakeFriendPort port = new FakeFriendPort();
        port.giveFriends(5, FriendListLimit.MAX_FRIENDS);
        int before = port.friendships.size();
        SendFriendRequestUseCase useCase = new SendFriendRequestUseCase(port);

        SendFriendRequestUseCase.Result result = useCase.execute(5, 7);

        assertThat(result).isEqualTo(SendFriendRequestUseCase.Result.FRIEND_LIST_FULL);
        assertThat(port.friendships).as("không lời mời nào được ghi").hasSize(before);
    }

    @Test
    void shouldStillSend_whenTheSenderIsOneBelowTheLimit() {
        FakeFriendPort port = new FakeFriendPort();
        port.giveFriends(5, FriendListLimit.MAX_FRIENDS - 1);
        SendFriendRequestUseCase useCase = new SendFriendRequestUseCase(port);

        assertThat(useCase.execute(5, 7)).isEqualTo(SendFriendRequestUseCase.Result.OK);
    }

    /** Dòng 71(c) (2026-10-17): 7 đã mời 5, giờ 5 mời lại 7 ⇒ hai người thành bạn luôn. */
    @Test
    void shouldBecomeFriends_whenTheTargetHasAlreadyInvitedTheSender() {
        FakeFriendPort port = new FakeFriendPort();
        port.friendships.add(new Friendship(1, 7, 5, FriendshipStatus.PENDING, Instant.now(), null));
        SendFriendRequestUseCase useCase = new SendFriendRequestUseCase(port);

        SendFriendRequestUseCase.Result result = useCase.execute(5, 7);

        assertThat(result).isEqualTo(SendFriendRequestUseCase.Result.BECAME_FRIENDS);
        assertThat(port.friendships).as("không đẻ thêm dòng ngược chiều").hasSize(1);
        assertThat(port.friendships.get(0).status()).isEqualTo(FriendshipStatus.ACCEPTED);
    }

    @Test
    void shouldLeaveTheReverseRequestPending_whenTheOriginalInvitersListIsFull() {
        FakeFriendPort port = new FakeFriendPort();
        port.friendships.add(new Friendship(1, 7, 5, FriendshipStatus.PENDING, Instant.now(), null));
        port.giveFriends(7, FriendListLimit.MAX_FRIENDS);
        SendFriendRequestUseCase useCase = new SendFriendRequestUseCase(port);

        SendFriendRequestUseCase.Result result = useCase.execute(5, 7);

        assertThat(result).isEqualTo(SendFriendRequestUseCase.Result.OTHER_FRIEND_LIST_FULL);
        assertThat(port.friendships.get(0).status()).isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    void shouldRefuse_whenTheSendersOwnListIsFullEvenForAReverseRequest() {
        FakeFriendPort port = new FakeFriendPort();
        port.friendships.add(new Friendship(1, 7, 5, FriendshipStatus.PENDING, Instant.now(), null));
        port.giveFriends(5, FriendListLimit.MAX_FRIENDS);
        SendFriendRequestUseCase useCase = new SendFriendRequestUseCase(port);

        assertThat(useCase.execute(5, 7)).isEqualTo(SendFriendRequestUseCase.Result.FRIEND_LIST_FULL);
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
            for (int i = 0; i < friendships.size(); i++) {
                Friendship f = friendships.get(i);
                if (f.id() == friendshipId
                        && f.addresseeId() == addresseeId
                        && f.status() == FriendshipStatus.PENDING) {
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
