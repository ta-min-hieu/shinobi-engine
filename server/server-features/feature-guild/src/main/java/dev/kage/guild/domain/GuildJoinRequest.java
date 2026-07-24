package dev.kage.guild.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 1 đơn xin gia nhập PENDING. Không có {@code status} — bảng chỉ chứa dòng PENDING, duyệt
 * (INSERT {@link GuildMember} + xoá dòng này) hoặc từ chối (xoá dòng) đều xoá dòng, cùng quyết
 * định "reject = xoá dòng" đã dùng ở {@code feature-friend} (không giữ lịch sử).
 */
public record GuildJoinRequest(long id, long guildId, long characterId, Instant requestedAt) {

    public GuildJoinRequest {
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
