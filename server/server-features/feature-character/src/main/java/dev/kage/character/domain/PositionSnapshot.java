package dev.kage.character.domain;

/**
 * Vị trí world đã LƯU (định kỳ + lúc logout, SERVER.md §8) — KHÔNG phải vị trí real-time trong
 * tick (đó là {@code server-world.component.CharacterIdComponent}/{@code PositionComponent}).
 * Dùng để tính điểm spawn khi nhân vật chọn vào world lần sau.
 *
 * <p>Toạ độ không hữu hạn ({@code NaN}/{@code Infinity}) bị đưa về {@code 0} — cùng chỗ nhân vật
 * chưa từng lưu vị trí xuất hiện (sửa 2026-10-03). Trước bản vá hướng NaN (2026-09-24) một gói
 * {@code MoveCommand} đủ để nhân vật mang toạ độ {@code NaN}, và cột {@code REAL} nhận nguyên giá
 * trị đó lúc thoát; không chặn ở đây thì nó đi thẳng vào {@code SelectCharacterResponse} mỗi lần vào
 * lại.
 */
public record PositionSnapshot(float x, float y) {

    public PositionSnapshot {
        x = Float.isFinite(x) ? x : 0f;
        y = Float.isFinite(y) ? y : 0f;
    }
}
