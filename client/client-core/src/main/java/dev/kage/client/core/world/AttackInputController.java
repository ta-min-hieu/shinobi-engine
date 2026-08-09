package dev.kage.client.core.world;

/**
 * Quyết định KHI NÀO gửi {@code AttackCommand} — đánh thường phải bấm tay từ 2026-08-09 (chủ dự án
 * quyết sau playtest: "tôi phải bấm đánh mới được đánh"; trước đó server tự đánh khi có mục tiêu).
 *
 * <p>Gộp hai nguồn vào một chỗ để {@code WorldScreen} chỉ phải hỏi một câu mỗi tick: phím
 * {@link dev.kage.client.core.input.GameAction#ATTACK} (giữ được, tự lặp) và nút Đánh trên HUD
 * (mỗi cú click là một lần). Không có lớp này thì hai đường phải tự đồng bộ đồng hồ lặp với nhau.
 *
 * <p><b>Nhịp lặp chỉ là phép LỊCH SỰ với đường truyền</b>: server mới là nơi giữ cooldown thật
 * ({@code CombatSystem}, 1 đòn/giây) và âm thầm bỏ qua lệnh thừa. Lệch nhịp ở đây cùng lắm là gửi
 * phí vài gói, không phải lỗ hổng — cùng tinh thần {@code WorldConstants.MOVE_SPEED_MPS}.
 */
final class AttackInputController {

    private boolean keyWasPressed;

    /** Còn bao lâu nữa mới gửi lệnh tiếp theo khi người chơi GIỮ nút/phím, giây. */
    private float repeatCountdownSeconds;

    /** Nút Đánh trên HUD vừa được click — chờ tick sau tiêu thụ, xem {@link #update}. */
    private boolean buttonClicked;

    /** Người chơi click nút Đánh trên HUD. Một click = một lần thử đánh, không quan tâm nhịp lặp. */
    void onButtonClicked() {
        buttonClicked = true;
    }

    /**
     * Gọi mỗi tick logic.
     *
     * @param attackKeyPressed phím đánh đang được GIỮ hay không.
     * @return {@code true} nếu tick này phải gửi một {@code AttackCommand}.
     */
    boolean update(boolean attackKeyPressed, float deltaTime) {
        boolean risingEdge = attackKeyPressed && !keyWasPressed;
        keyWasPressed = attackKeyPressed;

        if (repeatCountdownSeconds > 0f) {
            repeatCountdownSeconds = Math.max(0f, repeatCountdownSeconds - deltaTime);
        }
        // Click nút và lần VỪA nhấn phím đều bắn ngay, không chờ nhịp lặp — bấm mà không thấy phản
        // hồi tức thì là cảm giác "game bị đơ" dù server có nhận lệnh hay không.
        boolean immediate = risingEdge || buttonClicked;
        buttonClicked = false;
        if (!immediate && !(attackKeyPressed && repeatCountdownSeconds <= 0f)) {
            return false;
        }
        repeatCountdownSeconds = CombatConstants.ATTACK_INTERVAL_SECONDS;
        return true;
    }
}
