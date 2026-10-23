package dev.kage.friend.application;

import java.util.Optional;

import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;
import dev.kage.friend.domain.FriendshipStatus;

/**
 * Huỷ kết bạn — xoá quan hệ ACCEPTED giữa 2 nhân vật, bất kể ai từng gửi lời mời; và rút lời mời mình đã gửi
 * còn chờ (ADR-074).
 */
public final class RemoveFriendUseCase {

    private final FriendPort port;

    public RemoveFriendUseCase(FriendPort port) {
        this.port = port;
    }

    public Result execute(long characterId, long friendCharacterId) {
        return port.remove(characterId, friendCharacterId) ? Result.OK : Result.NOT_FOUND;
    }

    /**
     * ADR-074 — rút lời mời {@code requesterId} đã gửi cho {@code addresseeId}, khi nó còn PENDING. Lời mời chiều
     * ngược (người kia mời mình) không bị đụng. Xoá bằng đúng câu "từ chối" có điều kiện PENDING của port, nên lời
     * mời vừa được chấp nhận chen giữa thì không bị xoá; KHÔNG kích hoạt hạn chờ sau từ chối (dòng 71(b)).
     */
    public Result withdrawRequest(long requesterId, long addresseeId) {
        Optional<Friendship> pending = port.findBetween(requesterId, addresseeId)
                .filter(f -> f.status() == FriendshipStatus.PENDING && f.requesterId() == requesterId);
        if (pending.isEmpty()) {
            return Result.NOT_FOUND;
        }
        return port.reject(pending.get().id(), addresseeId) ? Result.OK : Result.NOT_FOUND;
    }

    public enum Result {
        OK,
        NOT_FOUND
    }
}
