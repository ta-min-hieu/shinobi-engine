package dev.kage.character.domain;

/**
 * Hệ phái (GDD §4) — MVP chỉ {@link #HOA} chơi được. "Chọn hệ ở Lv10" (GDD §4) là cơ chế Post-MVP
 * khi có đủ 5 hệ; nhân vật mới tạo ở MVP mặc định {@link #HOA}, không có bước chọn hệ lúc tạo.
 */
public enum Faction {
    HOA
    // THUY, PHONG, LOI, THO — thêm khi Post-MVP mở thêm hệ (GDD §4).
}
