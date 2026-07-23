package dev.kage.friend.api;

import dev.kage.friend.application.AcceptFriendRequestUseCase;
import dev.kage.friend.application.FriendUseCases;
import dev.kage.friend.application.RejectFriendRequestUseCase;
import dev.kage.friend.application.RemoveFriendUseCase;
import java.util.List;

/**
 * API công khai của {@code feature-friend} — module khác (server-gateway) CHỈ gọi qua đây, không
 * bao giờ đọc bảng {@code friendship} trực tiếp (Điều 24). Trạng thái online KHÔNG thuộc facade
 * này — gateway tự tra {@code ConnectedCharacterRegistry} sau khi có danh sách ID bạn bè (xem
 * feature-friend/CLAUDE.md).
 */
public final class FriendFacade {

    private final FriendUseCases useCases;

    public FriendFacade(FriendUseCases useCases) {
        this.useCases = useCases;
    }

    public SendRequestResult sendRequest(long requesterId, long addresseeId) {
        return switch (useCases.sendFriendRequestUseCase().execute(requesterId, addresseeId)) {
            case OK -> SendRequestResult.OK;
            case CANNOT_FRIEND_SELF -> SendRequestResult.CANNOT_FRIEND_SELF;
            case ALREADY_FRIENDS -> SendRequestResult.ALREADY_FRIENDS;
            case REQUEST_ALREADY_PENDING -> SendRequestResult.REQUEST_ALREADY_PENDING;
        };
    }

    public AcceptResult accept(long friendshipId, long addresseeId) {
        return switch (useCases.acceptFriendRequestUseCase().execute(friendshipId, addresseeId)) {
            case AcceptFriendRequestUseCase.Result.OK -> AcceptResult.OK;
            case AcceptFriendRequestUseCase.Result.NOT_FOUND -> AcceptResult.NOT_FOUND;
        };
    }

    public RejectResult reject(long friendshipId, long addresseeId) {
        return switch (useCases.rejectFriendRequestUseCase().execute(friendshipId, addresseeId)) {
            case RejectFriendRequestUseCase.Result.OK -> RejectResult.OK;
            case RejectFriendRequestUseCase.Result.NOT_FOUND -> RejectResult.NOT_FOUND;
        };
    }

    public RemoveResult remove(long characterId, long friendCharacterId) {
        return switch (useCases.removeFriendUseCase().execute(characterId, friendCharacterId)) {
            case RemoveFriendUseCase.Result.OK -> RemoveResult.OK;
            case RemoveFriendUseCase.Result.NOT_FOUND -> RemoveResult.NOT_FOUND;
        };
    }

    /** ID nhân vật của toàn bộ bạn bè đã ACCEPTED. */
    public List<Long> listFriends(long characterId) {
        return useCases.listFriendsUseCase().execute(characterId);
    }

    /** Lời mời đang chờ {@code characterId} chấp nhận/từ chối. */
    public List<PendingRequestView> listPendingRequests(long characterId) {
        return useCases.listPendingFriendRequestsUseCase().execute(characterId).stream()
                .map(f -> new PendingRequestView(f.id(), f.requesterId()))
                .toList();
    }

    public enum SendRequestResult { OK, CANNOT_FRIEND_SELF, ALREADY_FRIENDS, REQUEST_ALREADY_PENDING }

    public enum AcceptResult { OK, NOT_FOUND }

    public enum RejectResult { OK, NOT_FOUND }

    public enum RemoveResult { OK, NOT_FOUND }

    public record PendingRequestView(long friendshipId, long requesterId) {
    }
}
