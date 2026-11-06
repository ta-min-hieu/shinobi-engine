package dev.kage.guild.domain;

import java.time.Instant;
import java.util.Optional;

/** Thông báo bang (ADR-179) — một dòng do bang chủ ghi. Chạm DB. */
public interface GuildNoticePort {

    /** Kết quả ghi thông báo. */
    enum Outcome {
        OK,
        NOT_IN_GUILD,
        NOT_LEADER
    }

    /** Thông báo hiện tại của bang mà {@code characterId} đang ở; rỗng nếu không ở bang nào. "" = bang chưa có. */
    Optional<String> noticeOf(long characterId);

    /** Bang chủ ghi (hoặc xoá bằng "") thông báo — kèm một dòng audit. {@code notice} đã chuẩn hoá. */
    Outcome set(long characterId, String notice, Instant at);
}
