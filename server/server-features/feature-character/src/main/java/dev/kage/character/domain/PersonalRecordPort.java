package dev.kage.character.domain;

import java.time.Instant;
import java.util.Map;

/** Kỷ lục cá nhân (ADR-240). */
public interface PersonalRecordPort {

    /** Loại kỷ lục. */
    enum Kind {
        /** Sát thương một đòn cao nhất. */
        HIGHEST_HIT,
        /** Chuỗi hạ quái dài nhất không chết. */
        LONGEST_KILL_STREAK
    }

    /** Mọi kỷ lục đã có của {@code characterId} (thiếu loại nào = chưa có, coi như 0). */
    Map<Kind, Long> recordsOf(long characterId);

    /** Nâng kỷ lục lên {@code value} nếu lớn hơn bản đang lưu; {@code true} nếu đã nâng. */
    boolean raise(long characterId, Kind kind, long value, Instant at);
}
