package dev.kage.inventory.domain;

/** 9 slot trang bị (7 gốc + phi kiếm, linh thú — lô 17) của 1 nhân vật (DATABASE.md §2.2 — GDD §5 "Trang bị"). */
public enum EquipmentSlotType {
    WEAPON,
    ARMOR,
    HELMET,
    GLOVE,
    BOOT,
    ACCESSORY_1,
    ACCESSORY_2,
    /** Phi kiếm — chỉ tốc độ (ADR-070). */
    MOUNT,
    /** Linh thú — chỉ số tăng theo cấp linh thú (ADR-072). */
    PET
}
