package dev.kage.party.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 1 lời mời PENDING, hướng leader -> invitee (NGƯỢC với {@code GuildJoinRequest} — party mời
 * trực tiếp, giống feature-friend, khác guild "xin gia nhập"). Không có {@code status} — chấp
 * nhận (INSERT {@link PartyMember} + xoá dòng) hoặc từ chối (xoá dòng) đều xoá dòng, không giữ
 * lịch sử (cùng quyết định feature-friend/feature-guild).
 */
public record PartyInvite(long id, long partyId, long inviteeCharacterId, Instant invitedAt) {

    /**
     * Lời mời hết hiệu lực sau ngần này (quyết định chủ dự án 2026-10-11). Trước đó lời mời treo vĩnh
     * viễn: người được mời một lần rồi bỏ đó sẽ không bao giờ được nhóm KHÁC mời nữa (mỗi người chỉ
     * có một lời mời chờ), tới khi tự tay từ chối.
     */
    public static final Duration LIFETIME = Duration.ofMinutes(5);

    public PartyInvite {
        Objects.requireNonNull(invitedAt, "invitedAt");
    }

    /** Lời mời gửi TRƯỚC mốc này thì đã hết hạn tại thời điểm {@code now}. */
    public static Instant expiryCutoff(Instant now) {
        return now.minus(LIFETIME);
    }
}
