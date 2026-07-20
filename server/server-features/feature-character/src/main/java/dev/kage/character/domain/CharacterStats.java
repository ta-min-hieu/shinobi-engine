package dev.kage.character.domain;

/**
 * Chỉ số NỀN (str/agi/int/vit) đọc từ {@code character_stat} — KHÔNG phải chỉ số dẫn xuất
 * (ATK/DEF/HIT%...). Derive là việc của server-world theo công thức GDD §5
 * (feature-character/CLAUDE.md: "Chỉ số dẫn xuất — server tính lúc cần, không lưu").
 *
 * <p>{@code intelligence} thay vì {@code int} (từ khoá Java) — cột DB là {@code int_}.
 */
public record CharacterStats(int str, int agi, int intelligence, int vit) {
}
