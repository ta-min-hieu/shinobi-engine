package dev.kage.friend.domain;

/** Trạng thái 1 lời mời kết bạn. Không có REJECTED — từ chối = xoá dòng (xem {@link FriendPort#reject}). */
public enum FriendshipStatus {
    PENDING,
    ACCEPTED
}
