package dev.kage.checkin.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Luật quẻ bói (ADR-149) — hàm thuần. Ngày tính theo giờ Việt Nam như điểm danh. */
public final class FortuneRules {

    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    /** NPC gieo quẻ — trưởng lão Chu Hoành ở Tông Môn. */
    public static final String NPC_CODE = "town_elder";

    private static final long PERCENT = 100L;

    private FortuneRules() {}

    /** Ngày (giờ Việt Nam) chứa {@code now}. */
    public static LocalDate day(Instant now) {
        return now.atZone(VIETNAM).toLocalDate();
    }

    /** {@code amount} EXP sau quẻ {@code percent} (làm tròn xuống, không âm). */
    public static long boosted(long amount, int percent) {
        if (percent == 0 || amount <= 0) {
            return amount;
        }
        return Math.max(0L, amount * (PERCENT + percent) / PERCENT);
    }
}
