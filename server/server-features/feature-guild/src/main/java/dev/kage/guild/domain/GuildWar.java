package dev.kage.guild.domain;

import java.time.Instant;

/**
 * Một trận bang chiến (ADR-102) — dòng {@code guild_war}.
 *
 * @param id khoá trận
 * @param attacker bang tuyên chiến ({@code guild_a})
 * @param defender bang bị tuyên ({@code guild_b})
 * @param startsAt lúc bắt đầu được đánh
 * @param endsAt lúc hết giờ
 */
public record GuildWar(long id, Side attacker, Side defender, Instant startsAt, Instant endsAt) {

    /** Một phe: bang và số lần đã hạ thành viên phe kia. */
    public record Side(long guildId, String guildName, int kills) {}

    /** Đang trong giờ đánh tại {@code now}. */
    public boolean isActive(Instant now) {
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }

    /** Bang {@code guildId} là một trong hai phe. */
    public boolean involves(long guildId) {
        return attacker.guildId() == guildId || defender.guildId() == guildId;
    }
}
