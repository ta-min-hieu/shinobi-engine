package dev.kage.party.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 1 lời mời PENDING, hướng leader -> invitee (NGƯỢC với {@code GuildJoinRequest} — party mời
 * trực tiếp, giống feature-friend, khác guild "xin gia nhập"). Không có {@code status} — chấp
 * nhận (INSERT {@link PartyMember} + xoá dòng) hoặc từ chối (xoá dòng) đều xoá dòng, không giữ
 * lịch sử (cùng quyết định feature-friend/feature-guild).
 */
public record PartyInvite(long id, long partyId, long inviteeCharacterId, Instant invitedAt) {

    public PartyInvite {
        Objects.requireNonNull(invitedAt, "invitedAt");
    }
}
