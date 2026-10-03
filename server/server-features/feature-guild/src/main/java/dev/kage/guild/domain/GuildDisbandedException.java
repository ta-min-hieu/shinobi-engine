package dev.kage.guild.domain;

/**
 * Ném khi adapter ghi một dòng tham chiếu tới bang đã bị giải tán — leader giải tán giữa lúc use
 * case tra bang theo tên và lúc ghi (vi phạm FOREIGN KEY {@code guild_id}).
 */
public final class GuildDisbandedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GuildDisbandedException(long guildId) {
        super("Bang đã bị giải tán: guildId=" + guildId);
    }
}
