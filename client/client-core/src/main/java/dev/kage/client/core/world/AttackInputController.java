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
 *
 * <p><b>Thử lại khi chưa được xác nhận</b> (2026-10-01). Server bỏ IM LẶNG lệnh ngoài tầm 2m hoặc
 * còn trong cooldown và KHÔNG nhớ lại ý định ({@code CombatSystem.tryAttackNow}). Trước đây, bấm
 * đánh khi con quái còn ở 2,1m (đang lao tới) nghĩa là mất trọn một nhịp lặp ~1 giây không có gì
 * xảy ra — quái được đánh trước. Cooldown thật của server lại là 61 tick (~1,017s, do float) nên
 * nhịp giữ phím đúng 1s đôi khi tới SỚM một tick và cũng bị bỏ y như vậy. Vì thế mỗi lần gửi do
 * người chơi (nhấn, click, hoặc nhịp lặp khi giữ) mở một <b>cửa sổ chờ xác nhận</b>: tới khi thấy
 * {@code AttackResultEvent} do CHÍNH mình ra (trúng hay hụt đều tính — {@link #onAttackResult}),
 * lớp này gửi lại mỗi {@link CombatConstants#ATTACK_CONFIRM_RETRY_SECONDS}, tối đa
 * {@link CombatConstants#ATTACK_CONFIRM_WINDOW_SECONDS}. Cửa sổ đóng sớm khi nhả phím (trừ cửa sổ mở
 * bằng nút HUD — click không có "đang giữ"), khi đổi/mất mục tiêu, hoặc khi người gọi
 * {@link #cancelRetry()} (chết, mở hội thoại). Cùng cơ chế với {@code AttackCadence} của gameProbe.
 *
 * <p>Spam trong cửa sổ vô hại về gameplay (cooldown là của server), nhưng chi phí server tính THEO
 * GÓI (BENCHMARK §5.1) nên trần là ~10 gói/giây và chỉ khi đang chờ xác nhận; trường hợp thường
 * (trong tầm, RTT dưới {@code ATTACK_CONFIRM_RETRY_SECONDS}) không phát sinh gói thử lại nào.
 */
final class AttackInputController {

    /** Lệnh mà tick này phải gửi — enum để không cấp phát và để người gọi phân biệt hai loại. */
    enum Send {
        /** Không gửi gì. */
        NONE,
        /** Lần gửi do người chơi (nhấn/click/nhịp lặp) — người gọi báo chữ nổi + phát animation. */
        INTENT,
        /** Gửi lại IM LẶNG vì chưa thấy xác nhận — không chữ nổi, không animation lặp lại. */
        RETRY
    }

    /** Không có mục tiêu — khớp {@code CombatHudController.currentTargetId()} khi chưa nhắm ai. */
    private static final long NO_TARGET = 0L;

    /** Id nhân vật của chính mình, để nhận ra đòn nào trong {@code AttackResultEvent} là của MÌNH. */
    private final long localCharacterId;

    private boolean keyWasPressed;

    /** Còn bao lâu nữa mới gửi lệnh tiếp theo khi người chơi GIỮ nút/phím, giây. */
    private float repeatCountdownSeconds;

    /** Nút Đánh trên HUD vừa được click — chờ tick sau tiêu thụ, xem {@link #update}. */
    private boolean buttonClicked;

    /** Đang trong cửa sổ chờ xác nhận — xem JavaDoc lớp. */
    private boolean awaitingConfirmation;

    /** Cửa sổ chờ xác nhận do click nút mở — không bị đóng bởi việc phím không được giữ. */
    private boolean windowFromButton;

    /** Mục tiêu lúc mở cửa sổ; đổi mục tiêu thì thôi thử lại (ý định cũ không còn). */
    private long pendingTargetId;

    private float windowRemainingSeconds;

    private float retryCountdownSeconds;

    /** @param localCharacterId id THẬT của chính người chơi, khớp {@code attacker_entity_id} server gửi. */
    AttackInputController(long localCharacterId) {
        this.localCharacterId = localCharacterId;
    }

    /** Người chơi click nút Đánh trên HUD. Một click = một lần thử đánh, không quan tâm nhịp lặp. */
    void onButtonClicked() {
        buttonClicked = true;
    }

    /**
     * Gọi mỗi tick logic.
     *
     * @param attackKeyPressed phím đánh đang được GIỮ hay không.
     * @param deltaTime bước tick, giây.
     * @param targetId mục tiêu hiện tại ({@code 0} = không có) — đổi mục tiêu là đóng cửa sổ thử lại.
     * @return tick này phải gửi gì.
     */
    Send update(boolean attackKeyPressed, float deltaTime, long targetId) {
        boolean risingEdge = attackKeyPressed && !keyWasPressed;
        keyWasPressed = attackKeyPressed;
        repeatCountdownSeconds = countDown(repeatCountdownSeconds, deltaTime);
        windowRemainingSeconds = countDown(windowRemainingSeconds, deltaTime);
        retryCountdownSeconds = countDown(retryCountdownSeconds, deltaTime);

        // Click nút và lần VỪA nhấn phím đều bắn ngay, không chờ nhịp lặp — bấm mà không thấy phản
        // hồi tức thì là cảm giác "game bị đơ" dù server có nhận lệnh hay không.
        boolean clicked = buttonClicked;
        buttonClicked = false;
        if (risingEdge || clicked || (attackKeyPressed && repeatCountdownSeconds <= 0f)) {
            repeatCountdownSeconds = CombatConstants.ATTACK_INTERVAL_SECONDS;
            openWindow(targetId, clicked);
            return Send.INTENT;
        }
        return retryIfDue(attackKeyPressed, targetId);
    }

    /**
     * Một {@code AttackResultEvent} vừa tới. Chỉ đòn của CHÍNH mình mới là xác nhận — đòn của người
     * khác/quái nhắm cùng con quái thì không nói gì về lệnh của mình.
     *
     * <p>Nhịp lặp kế tiếp được đo từ lúc THẤY xác nhận, không từ lần gửi cuối (có thể đã bị bỏ) —
     * để nhát sau không tới sớm hơn hẳn cooldown rồi lại phải thử lại.
     *
     * @param attackerEntityId {@code attacker_entity_id} của event.
     */
    void onAttackResult(long attackerEntityId) {
        if (attackerEntityId != localCharacterId || !awaitingConfirmation) {
            return;
        }
        awaitingConfirmation = false;
        repeatCountdownSeconds = CombatConstants.ATTACK_INTERVAL_SECONDS;
    }

    /** Thôi thử lại ngay (chết, mở hội thoại...). Không đụng nhịp lặp của phím đang giữ. */
    void cancelRetry() {
        awaitingConfirmation = false;
    }

    /**
     * Có đang trong cửa sổ chờ xác nhận không — cho test và chẩn đoán.
     *
     * @return {@code true} nếu còn đang chờ {@code AttackResultEvent} của chính mình.
     */
    boolean isAwaitingConfirmation() {
        return awaitingConfirmation;
    }

    private void openWindow(long targetId, boolean fromButton) {
        // Không mục tiêu thì server không làm gì ⇒ chẳng có gì để chờ xác nhận.
        awaitingConfirmation = targetId != NO_TARGET;
        windowFromButton = fromButton;
        pendingTargetId = targetId;
        windowRemainingSeconds = CombatConstants.ATTACK_CONFIRM_WINDOW_SECONDS;
        retryCountdownSeconds = CombatConstants.ATTACK_CONFIRM_RETRY_SECONDS;
    }

    private Send retryIfDue(boolean attackKeyPressed, long targetId) {
        if (!awaitingConfirmation) {
            return Send.NONE;
        }
        boolean intentGone = !attackKeyPressed && !windowFromButton;
        if (intentGone || targetId != pendingTargetId || windowRemainingSeconds <= 0f) {
            awaitingConfirmation = false;
            return Send.NONE;
        }
        if (retryCountdownSeconds > 0f) {
            return Send.NONE;
        }
        retryCountdownSeconds = CombatConstants.ATTACK_CONFIRM_RETRY_SECONDS;
        return Send.RETRY;
    }

    private static float countDown(float remainingSeconds, float deltaTime) {
        return remainingSeconds > 0f ? Math.max(0f, remainingSeconds - deltaTime) : remainingSeconds;
    }
}
