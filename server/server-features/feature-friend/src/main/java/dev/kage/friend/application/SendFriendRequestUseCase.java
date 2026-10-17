package dev.kage.friend.application;

import java.util.Optional;

import dev.kage.friend.domain.DuplicateFriendRequestException;
import dev.kage.friend.domain.FriendListLimit;
import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;
import dev.kage.friend.domain.FriendshipStatus;

/**
 * Gửi lời mời kết bạn — kiểm tự-mời/đã-bạn/đã-có-lời-mời TRƯỚC khi chạm DB. Mời ngược chiều một lời
 * mời đang chờ thì chấp nhận nó thay vì từ chối (xem {@link #acceptReverseRequest}).
 */
public final class SendFriendRequestUseCase {

    private final FriendPort port;

    /** Mời ngược chiều một lời mời đang chờ là CHẤP NHẬN nó — đi đúng đường kiểm trần của chấp nhận. */
    private final AcceptFriendRequestUseCase accept;

    private final FriendRequestCooldowns cooldowns;

    public SendFriendRequestUseCase(FriendPort port) {
        this(port, new FriendRequestCooldowns());
    }

    /** {@code cooldowns} dùng chung với {@link RejectFriendRequestUseCase} — xem {@link FriendRequestCooldowns}. */
    public SendFriendRequestUseCase(FriendPort port, FriendRequestCooldowns cooldowns) {
        this.port = port;
        this.accept = new AcceptFriendRequestUseCase(port);
        this.cooldowns = cooldowns;
    }

    public Result execute(long requesterId, long addresseeId) {
        if (requesterId == addresseeId) {
            return Result.CANNOT_FRIEND_SELF;
        }
        Optional<Friendship> existing = port.findBetween(requesterId, addresseeId);
        if (existing.isPresent()) {
            Friendship relationship = existing.get();
            if (relationship.status() == FriendshipStatus.ACCEPTED) {
                return Result.ALREADY_FRIENDS;
            }
            return relationship.requesterId() == addresseeId
                    ? acceptReverseRequest(relationship, requesterId)
                    : Result.REQUEST_ALREADY_PENDING;
        }
        // Danh sách của NGƯỜI GỬI đã đầy thì không mời thêm; danh sách người nhận được kiểm lúc họ chấp nhận.
        if (FriendListLimit.isFull(port.listAccepted(requesterId).size())) {
            return Result.FRIEND_LIST_FULL;
        }
        // Hai trần chống làm phiền (dòng 71(b), 2026-10-17): vừa bị người này từ chối thì chờ; hộp lời mời
        // của người nhận đầy thì không xếp thêm.
        if (cooldowns.isCoolingDown(requesterId, addresseeId)) {
            return Result.RECENTLY_REJECTED;
        }
        if (FriendListLimit.isPendingFull(port.listPendingIncoming(addresseeId).size())) {
            return Result.TARGET_PENDING_FULL;
        }
        try {
            port.sendRequest(requesterId, addresseeId);
        } catch (DuplicateFriendRequestException e) {
            return Result.REQUEST_ALREADY_PENDING;
        }
        return Result.OK;
    }

    /**
     * Người kia ĐÃ mời mình, giờ mình mời lại họ: hai bên cùng muốn kết bạn nên kết luôn (dòng 71(c),
     * chủ dự án duyệt 2026-10-17). Trước đó người mời sau nhận {@link Result#REQUEST_ALREADY_PENDING}
     * — một câu nói đúng nhưng không cho biết lời mời đang chờ là của AI, và không có lệnh nào khác
     * ngoài mở mục lời mời ra tự bấm Đồng ý.
     */
    private Result acceptReverseRequest(Friendship incoming, long accepterId) {
        return switch (accept.execute(incoming.id(), accepterId)) {
            case OK -> Result.BECAME_FRIENDS;
            case FRIEND_LIST_FULL -> Result.FRIEND_LIST_FULL;
            case REQUESTER_FRIEND_LIST_FULL -> Result.OTHER_FRIEND_LIST_FULL;
            // Lời mời vừa bị rút/chấp nhận giữa lúc đọc và lúc ghi: trạng thái mới nhất mới đúng.
            case NOT_FOUND ->
                port.findBetween(accepterId, incoming.requesterId())
                        .map(now -> now.status() == FriendshipStatus.ACCEPTED
                                ? Result.ALREADY_FRIENDS
                                : Result.REQUEST_ALREADY_PENDING)
                        .orElse(Result.REQUEST_ALREADY_PENDING);
        };
    }

    public enum Result {
        OK,
        CANNOT_FRIEND_SELF,
        ALREADY_FRIENDS,
        REQUEST_ALREADY_PENDING,
        /** Danh sách bạn bè của người gửi đã chạm {@link FriendListLimit#MAX_FRIENDS} (2026-10-11). */
        FRIEND_LIST_FULL,
        /** Mời ngược chiều một lời mời đang chờ ⇒ hai người vừa thành bạn (2026-10-17). */
        BECAME_FRIENDS,
        /** Mời ngược chiều nhưng danh sách của người mời trước đã đầy — lời mời của họ còn nguyên. */
        OTHER_FRIEND_LIST_FULL,
        /** Người nhận vừa từ chối lời mời của người gửi — còn trong {@link FriendRequestCooldowns#COOLDOWN}. */
        RECENTLY_REJECTED,
        /** Người nhận đã có {@link FriendListLimit#MAX_PENDING_INCOMING} lời mời đang chờ. */
        TARGET_PENDING_FULL
    }
}
