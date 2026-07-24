package dev.kage.guild.domain;

/** Ném khi adapter phát hiện vi phạm UNIQUE {@code (guild_id, character_id)} ở {@code guild_join_request}. */
public final class DuplicateGuildJoinRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateGuildJoinRequestException(long guildId, long characterId) {
        super("Đã có đơn xin gia nhập: guildId=" + guildId + ", characterId=" + characterId);
    }
}
