package dev.kage.guild.domain;

import java.time.Instant;
import java.time.LocalDate;

/** Điểm danh bang (ADR-186). Chạm DB. */
public interface GuildCheckInPort {

    /** Cống hiến cộng cho người điểm danh. PLACEHOLDER (Điều 27). */
    long CONTRIBUTION = 100L;

    /** Kinh nghiệm cộng cho bang. PLACEHOLDER (Điều 27). */
    long GUILD_EXP = 100L;

    /** Kết quả điểm danh. */
    enum Outcome {
        OK,
        NOT_IN_GUILD,
        ALREADY_CHECKED_IN
    }

    /**
     * Kết quả + số người của bang đã điểm danh hôm nay (kể cả mình), bang có vừa lên cấp không, id bang.
     *
     * @param todayCount 0 khi {@code NOT_IN_GUILD}
     */
    record CheckIn(Outcome outcome, int todayCount, boolean leveledUp, long guildId) {}

    /**
     * Một transaction: ghi lượt (một lần / nhân vật / ngày), cộng cống hiến, cộng kinh nghiệm bang (khoá dòng bang).
     */
    CheckIn checkIn(long characterId, LocalDate day, Instant at);
}
