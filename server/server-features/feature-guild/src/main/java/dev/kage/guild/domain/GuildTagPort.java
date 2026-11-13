package dev.kage.guild.domain;

import java.util.Optional;

/** Ký hiệu bang (ADR-344) — cột {@code guild.tag}. */
public interface GuildTagPort {

    /** Ký hiệu của bang (rỗng nếu chưa đặt). */
    Optional<String> tagOf(long guildId);

    /** Ký hiệu bang của nhân vật (rỗng nếu không ở bang nào hoặc bang chưa đặt). */
    Optional<String> tagOfCharacter(long characterId);

    /** Đặt ({@code null} = xoá); {@code false} nếu ký hiệu đã có bang khác dùng. */
    boolean setTag(long guildId, String tag);
}
