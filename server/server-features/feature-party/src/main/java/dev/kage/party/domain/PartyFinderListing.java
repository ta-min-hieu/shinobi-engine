package dev.kage.party.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Một tin tìm nhóm (ADR-082).
 *
 * @param leaderId trưởng nhóm đăng tin
 * @param leaderName tên hiển thị của trưởng nhóm lúc đăng
 * @param goal mục tiêu
 * @param minLevel cấp tối thiểu để vào
 * @param expiresAt hết hạn lúc này
 */
public record PartyFinderListing(long leaderId, String leaderName, PartyGoal goal, int minLevel, Instant expiresAt) {

    public PartyFinderListing {
        Objects.requireNonNull(leaderName, "leaderName");
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
