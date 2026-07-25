package dev.kage.character.domain;

/**
 * Hệ phái (GDD §4) — nhân vật mới tạo LUÔN {@link #HOA} ({@code CreateCharacterUseCase}), 4 hệ còn
 * lại chọn qua {@code SelectFactionUseCase} khi đủ Lv10 (Post-MVP, Phase 8). Thứ tự khai báo enum
 * KHÔNG mang ý nghĩa khắc chế — bảng khắc chế thật nằm ở {@code server-world.ElementCounterTable}
 * (module này không biết combat, chỉ là danh tính hệ phái).
 */
public enum Faction {
    HOA,
    THUY,
    PHONG,
    LOI,
    THO
}
