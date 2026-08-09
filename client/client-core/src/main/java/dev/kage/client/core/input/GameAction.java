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
    JUMP,

    /**
     * Dùng kỹ năng đang chọn lên mục tiêu đang chọn (chủ dự án yêu cầu 2026-08-09) — trước đó chỉ
     * bấm được nút trên HUD bằng chuột, tức đang chạy bằng bàn phím thì phải rời tay ra mới đánh
     * được. Chọn mục tiêu KHÔNG phải một {@code GameAction}: đó là thao tác trỏ vào một điểm cụ thể
     * trên màn hình, không phải ý định nhị phân bấm/không bấm.
     */
    USE_SKILL,

    /**
     * Đánh thường một đòn lên mục tiêu đang chọn (chủ dự án yêu cầu 2026-08-09: "tôi phải bấm đánh
     * mới được đánh"). Trước đó server tự đánh khi có mục tiêu nên không cần ý định nào cả.
     */
    ATTACK
}
