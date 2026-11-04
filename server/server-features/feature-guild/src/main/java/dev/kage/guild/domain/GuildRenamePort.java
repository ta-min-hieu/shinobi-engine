package dev.kage.guild.domain;

import java.time.Duration;
import java.time.Instant;

/** Đổi tên bang hội (ADR-172) — chỉ bang chủ, tốn vàng của chính bang chủ, hồi chiêu 30 ngày. */
public interface GuildRenamePort {

    /** Phí đổi tên — trừ vào vàng của bang chủ (dự án chưa có quỹ vàng bang). PLACEHOLDER (Điều 27). */
    long FEE = 20_000L;

    /** Hồi chiêu giữa hai lần đổi. */
    Duration COOLDOWN = Duration.ofDays(30);

    /** Kết quả đổi tên. */
    enum Outcome {
        OK,
        NOT_IN_GUILD,
        NOT_LEADER,
        INVALID_NAME,
        NAME_TAKEN,
        ON_COOLDOWN,
        NOT_ENOUGH_GOLD
    }

    /**
     * Kết quả + tên mới, id bang (để báo thành viên), lúc được đổi lần sau ({@code ON_COOLDOWN}/OK).
     *
     * @param nextAllowedAt {@link Instant#EPOCH} khi không có ý nghĩa
     */
    record Renamed(Outcome outcome, String name, long guildId, Instant nextAllowedAt) {

        /** Từ chối — không đổi gì. */
        public static Renamed refused(Outcome outcome) {
            return new Renamed(outcome, "", 0L, Instant.EPOCH);
        }
    }

    /** Một transaction: kiểm bang chủ, khoá dòng bang, hồi chiêu, tên trùng, trừ phí, đổi tên, audit. */
    Renamed rename(long characterId, String newName, Instant at);
}
