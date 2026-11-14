package dev.kage.character.domain;

import java.time.LocalDate;

/** Thích hồ sơ (ADR-298) — bảng {@code profile_like}: một lượt mỗi (người thích, người được thích, ngày). */
public interface ProfileLikePort {

    /** Ghi một lượt thích; {@code false} nếu hôm nay đã thích người này rồi. */
    boolean like(long likerId, long targetId, LocalDate day);

    /** ADR-305 — một dòng bảng xếp hạng lượt thích. */
    record Ranked(String name, int likes) {}

    /** ADR-305 — top {@code limit} người được thích nhiều nhất. */
    java.util.List<Ranked> top(int limit);

    /** ADR-384 — top {@code limit} theo lượt thích nhận từ ngày {@code from} (gồm cả ngày đó) trở đi. */
    java.util.List<Ranked> topSince(LocalDate from, int limit);

    /** Tổng lượt thích {@code targetId} đã nhận. */
    int likesOf(long targetId);
}
