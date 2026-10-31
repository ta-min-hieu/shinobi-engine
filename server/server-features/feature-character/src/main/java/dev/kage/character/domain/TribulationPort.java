package dev.kage.character.domain;

/** Thiên Kiếp (ADR-126, V106) — cột {@code character.tribulations_passed}; hiện thực ở server-database. */
public interface TribulationPort {

    /** Số kiếp đã qua (0..5). */
    int passed(long characterId);

    /** Qua một kiếp — compare-and-set từ {@code expectedPassed}; {@code false} nếu đã có lượt khác ghi trước. */
    boolean advance(long characterId, int expectedPassed);

    /** Trừ {@code amount} EXP nhưng không xuống dưới {@code floorExp}. */
    void penalize(long characterId, long amount, long floorExp);
}
