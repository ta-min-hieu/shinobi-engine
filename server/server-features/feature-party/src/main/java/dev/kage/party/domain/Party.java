package dev.kage.party.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root — 1 party. Không có {@code name} (khác {@code Guild}) — party trong GDD không
 * được đặt tên, chỉ có leader + thành viên. Thành viên KHÔNG nhúng ở đây, xem {@link PartyMember}/
 * {@link PartyPort}.
 */
public record Party(long id, long leaderCharacterId, Instant createdAt) {

    /** GDD §Bí Cảnh: "party (2–5)" — 5 là sức chứa tối đa 1 party. */
    public static final int MAX_MEMBERS = 5;

    public Party {
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
