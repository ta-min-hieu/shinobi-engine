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

class RemoveFriendUseCaseTest {

    @Test
    void shouldRemove_whenAcceptedFriendshipExists() {
        FakeFriendPort port = new FakeFriendPort();
        port.friendships.add(new Friendship(1, 5, 7, FriendshipStatus.ACCEPTED, Instant.now(), Instant.now()));
        RemoveFriendUseCase useCase = new RemoveFriendUseCase(port);

        RemoveFriendUseCase.Result result = useCase.execute(5, 7);

        assertThat(result).isEqualTo(RemoveFriendUseCase.Result.OK);
        assertThat(port.friendships).isEmpty();
    }

    @Test
    void shouldReject_whenNotFriends() {
        RemoveFriendUseCase useCase = new RemoveFriendUseCase(new FakeFriendPort());

        RemoveFriendUseCase.Result result = useCase.execute(5, 7);

        assertThat(result).isEqualTo(RemoveFriendUseCase.Result.NOT_FOUND);
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
            return friendships.removeIf(f -> f.status() == FriendshipStatus.ACCEPTED
                    && f.involves(characterId) && f.involves(friendCharacterId));
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
