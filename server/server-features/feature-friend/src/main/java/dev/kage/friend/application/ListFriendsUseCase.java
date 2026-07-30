package dev.kage.friend.application;

import java.util.List;

import dev.kage.friend.domain.FriendPort;

/** Danh sách ID nhân vật bạn bè (ACCEPTED) của {@code characterId} — trạng thái online tra riêng ở gateway. */
public final class ListFriendsUseCase {

    private final FriendPort port;

    public ListFriendsUseCase(FriendPort port) {
        this.port = port;
    }

    public List<Long> execute(long characterId) {
        return port.listAccepted(characterId).stream()
                .map(f -> f.otherParty(characterId))
                .toList();
    }
}
