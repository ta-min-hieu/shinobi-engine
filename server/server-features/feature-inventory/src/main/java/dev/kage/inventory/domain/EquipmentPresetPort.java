package dev.kage.inventory.domain;

import java.util.Map;

/** Lưu bộ trang bị (ADR-175) — mỗi bộ là ánh xạ ô → id món. Chạm DB. */
public interface EquipmentPresetPort {

    /** Mọi bộ đã lưu của nhân vật, theo số bộ (1..). Chưa lưu bộ nào ⇒ map rỗng. */
    Map<Integer, Map<EquipmentSlotType, Long>> presetsOf(long characterId);

    /** Thay TRỌN bộ {@code presetNo} bằng {@code slots} (một transaction). */
    void replace(long characterId, int presetNo, Map<EquipmentSlotType, Long> slots);
}
