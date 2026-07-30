package dev.kage.friend.application;

import dev.kage.friend.domain.FriendPort;

/** Huỷ kết bạn — xoá quan hệ ACCEPTED giữa 2 nhân vật, bất kể ai từng gửi lời mời. */
public final class RemoveFriendUseCase {

    private final FriendPort port;

    public RemoveFriendUseCase(FriendPort port) {
        this.port = port;
    }

    public Result execute(long characterId, long friendCharacterId) {
        return port.remove(characterId, friendCharacterId) ? Result.OK : Result.NOT_FOUND;
    }

    public enum Result {
        OK,
        NOT_FOUND
    }
}
