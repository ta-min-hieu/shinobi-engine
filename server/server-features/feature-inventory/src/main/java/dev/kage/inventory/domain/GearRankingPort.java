package dev.kage.inventory.domain;

import java.util.List;

/** Xếp hạng linh thú (ADR-439) và cường hoá (ADR-440) — đọc {@code pet_state} / {@code inventory_item}. */
public interface GearRankingPort {

    /** Một dòng bảng: tên nhân vật và điểm. */
    record Ranked(String name, long value) {}

    /** Mỗi nhân vật một dòng: cấp linh thú cao nhất. */
    List<Ranked> topPets(int limit);

    /** Mỗi nhân vật một dòng: cấp cường hoá cao nhất (> 0). */
    List<Ranked> topEnhance(int limit);
}
