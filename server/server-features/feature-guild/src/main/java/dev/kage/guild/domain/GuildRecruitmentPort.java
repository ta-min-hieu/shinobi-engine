package dev.kage.guild.domain;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;

/** Bảng chiêu mộ bang (ADR-185). Chạm DB. */
public interface GuildRecruitmentPort {

    /** Kết quả treo / gỡ chiêu mộ. */
    enum Outcome {
        OK,
        NOT_IN_GUILD,
        /** Không phải bang chủ hay phó bang chủ. */
        NOT_ALLOWED
    }

    /** Một dòng trên bảng. */
    record Listing(String guildName, int guildLevel, int members, int minLevel, String message, Instant updatedAt) {}

    /** Treo ({@code message} ≠ null) hoặc gỡ ({@code message} = null) chiêu mộ của bang mà {@code characterId} ở. */
    Outcome set(long characterId, int minLevel, String message, Instant at);

    /** Các bang đang chiêu mộ — cấp bang giảm dần, rồi mới cập nhật trước; tối đa {@code limit} dòng. */
    List<Listing> list(int limit);

    /** Cấp tối thiểu bang tên {@code guildName} đang đòi (rỗng nếu không chiêu mộ / không có bang). */
    OptionalInt minLevelOf(String guildName);
}
