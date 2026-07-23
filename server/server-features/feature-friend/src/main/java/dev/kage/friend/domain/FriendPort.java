package dev.kage.friend.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port — DB-agnostic (Điều 23). Implementation thật ({@code PostgresFriendAdapter}) nằm ở
 * server-database. Không cần {@code Transaction}/{@code TransactionRunner} riêng như feature-shop/
 * feature-quest — mọi thao tác ở đây là 1 câu SQL duy nhất (không có gold/item đi kèm cần nguyên
 * tử hoá xuyên nhiều bảng, DATABASE.md §5 không áp dụng ở feature này).
 */
public interface FriendPort {

    /** Quan hệ (PENDING hoặc ACCEPTED) giữa 2 nhân vật, bất kể ai là requester — rỗng nếu chưa có. */
    Optional<Friendship> findBetween(long characterIdA, long characterIdB);

    /**
     * Tạo lời mời PENDING mới.
     *
     * @throws DuplicateFriendRequestException nếu vi phạm UNIQUE (requester_id, addressee_id)
     */
    Friendship sendRequest(long requesterId, long addresseeId);

    /**
     * Chấp nhận — UPDATE có điều kiện {@code WHERE id=? AND addressee_id=? AND status='PENDING'}.
     *
     * @return {@code true} nếu có dòng bị đổi (lời mời tồn tại, đúng người, còn PENDING)
     */
    boolean accept(long friendshipId, long addresseeId);

    /** Từ chối — xoá dòng PENDING (không giữ trạng thái REJECTED, xem {@link FriendshipStatus}). */
    boolean reject(long friendshipId, long addresseeId);

    /** Huỷ kết bạn — xoá dòng ACCEPTED giữa 2 nhân vật, bất kể ai từng là requester. */
    boolean remove(long characterId, long friendCharacterId);

    /** Toàn bộ quan hệ ACCEPTED có liên quan tới {@code characterId} (ở vai trò bất kỳ). */
    List<Friendship> listAccepted(long characterId);

    /** Lời mời PENDING mà {@code characterId} là người NHẬN (chờ chấp nhận/từ chối). */
    List<Friendship> listPendingIncoming(long characterId);
}
