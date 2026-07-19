package dev.kage.character.domain;

/**
 * Vị trí world đã LƯU (định kỳ + lúc logout, SERVER.md §8) — KHÔNG phải vị trí real-time trong
 * tick (đó là {@code server-world.component.CharacterIdComponent}/{@code PositionComponent}).
 * Dùng để tính điểm spawn khi nhân vật chọn vào world lần sau.
 */
public record PositionSnapshot(float x, float y) {
}
