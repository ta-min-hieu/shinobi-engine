package dev.kage.client.core.input;

/**
 * Ý ĐỊNH của người chơi — gameplay code không biết phím nào được bấm, chỉ biết ý định
 * (CLIENT.md §5). Đổi từ bàn phím sang cảm ứng = đổi ánh xạ ở {@link InputMapper}, không đụng
 * gameplay.
 *
 * <p>ADR-033 (2026-08-08): bỏ {@code MOVE_UP}/{@code MOVE_DOWN} — di chuyển chỉ còn trái/phải,
 * thay bằng {@link #JUMP} (kiểu Ninja School Online, nhảy có tác dụng gameplay thật, không chỉ
 * hoạt ảnh).
 */
public enum GameAction {
    MOVE_LEFT,
    MOVE_RIGHT,
    JUMP
}
