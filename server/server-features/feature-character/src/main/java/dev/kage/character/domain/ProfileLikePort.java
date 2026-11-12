package dev.kage.character.domain;

import java.time.LocalDate;

/** Thích hồ sơ (ADR-298) — bảng {@code profile_like}: một lượt mỗi (người thích, người được thích, ngày). */
public interface ProfileLikePort {

    /** Ghi một lượt thích; {@code false} nếu hôm nay đã thích người này rồi. */
    boolean like(long likerId, long targetId, LocalDate day);

    /** Tổng lượt thích {@code targetId} đã nhận. */
    int likesOf(long targetId);
}
