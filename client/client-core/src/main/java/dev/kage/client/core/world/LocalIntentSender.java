package dev.kage.client.core.world;

import java.util.Optional;
import java.util.function.BooleanSupplier;

import dev.kage.client.core.input.GameAction;
import dev.kage.client.core.input.InputMapper;
import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.protocol.AttackResultEvent;

/**
 * Biến phím người chơi đang bấm thành <b>Ý ĐỊNH</b> gửi lên server — đi lại, nhảy, đánh thường,
 * dùng skill. Tách khỏi {@link WorldScreen} để lớp đó chỉ còn lo vẽ và định tuyến gói.
 *
 * <p><b>Server Authority (CLIENT.md §1).</b> Không có gì ở đây quyết định kết quả gameplay: lớp này
 * chỉ gửi ý định và dự đoán CỤC BỘ hai thứ được phép dự đoán — vận tốc của chính mình (CLIENT.md §6,
 * chạy cùng {@code MovementSystem} với server) và animation của chính mình (animation không quyết
 * định gì, {@code AttackResultEvent}/{@code SkillResultEvent} tới sau mới là sự thật).
 *
 * <p><b>Vì sao vài lệnh KHÔNG gửi khi thiếu điều kiện, vài lệnh vẫn gửi.</b> Không có mục tiêu thì
 * server cũng không làm gì ⇒ không gửi gói thừa. Nhưng ngoài tầm đánh thì VẪN gửi: client chỉ ĐOÁN
 * tầm ({@link CombatConstants#ATTACK_RANGE_METERS}), server mới quyết. Cả hai trường hợp đều nói ra
 * bằng chữ nổi thay vì im lặng — người chơi bấm mãi không hiểu gì là lỗi tệ hơn một gói thừa.
 */
final class LocalIntentSender {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<VelocityComponent> VELOCITY = ComponentMapper.getFor(VelocityComponent.class);

    private final LocalIntentContext context;

    /** ADR-070 — hệ số tốc độ phi kiếm theo lời server (1 = không có); chỉ để dự đoán. */
    private float speedMultiplier = 1f;

    /** ADR-073 — hệ số tốc độ do hiệu ứng trạng thái server báo (choáng tính riêng ở {@link #immobilized}). */
    private float statusSpeedMultiplier = 1f;

    private boolean immobilized;

    /** Hướng đi gửi lần cuối — chỉ gửi lại khi ĐỔI, không phải mỗi tick (60 gói/giây là vô nghĩa). */
    private float lastSentDirX;

    /**
     * Toàn bộ trạng thái thời gian của nút nhảy (mép nhấn + đồng hồ giữ nút) — {@link GameAction}
     * chỉ nói "đang giữ hay không", biến nó thành cú nhảy đơn/đôi là việc của lớp kia.
     */
    private final JumpInputController jumpInput = new JumpInputController();

    /** Phím Z + nút Đánh trên HUD gộp về một nguồn — xem {@link AttackInputController}. */
    private final AttackInputController attackInput;

    /**
     * Mép nhấn của phím dùng skill — giữ phím không spam (nhảy có bộ đếm riêng ở {@link #jumpInput}),
     * và Enter vừa GỬI một ô nhập không bị đọc lại thành lệnh dùng skill (xem {@link KeyPressEdge}).
     */
    private final KeyPressEdge skillKey = new KeyPressEdge();

    /** ADR-133 — mép nhấn phím pháp bảo; hành động do {@link #setOnArtifact} nối (ArtifactCoordinator). */
    private final KeyPressEdge artifactKey = new KeyPressEdge();

    private Runnable onArtifact = () -> {};

    /** ADR-206 — đích tự đi; mặc định một đích không bao giờ được đặt. */
    private AutoWalkTarget autoWalk = new AutoWalkTarget();

    /** ADR-206 — vị trí X ở lần kiểm tiến độ gần nhất, và số tick đã tự đi mà không nhích được. */
    private float autoWalkCheckX;

    private int autoWalkStalledTicks;

    LocalIntentSender(LocalIntentContext context) {
        this.context = context;
        this.attackInput = new AttackInputController(context.localCharacterId());
    }

    /**
     * Mọi {@code AttackResultEvent} đi qua đây — đòn của CHÍNH mình là xác nhận, chấm dứt cửa sổ gửi
     * lại của {@link AttackInputController}. Đòn của người khác/quái bị lớp kia bỏ qua.
     */
    void onAttackResult(AttackResultEvent event) {
        attackInput.onAttackResult(event.getAttackerEntityId());
    }

    /** Nút Đánh trên HUD — cùng nguồn với phím Z, xem {@link AttackInputController}. */
    void onAttackButtonClicked() {
        attackInput.onButtonClicked();
    }

    /**
     * ADR-051 — vừa đổi World. Phía server đó là một entity MỚI với vận tốc 0
     * ({@code WorldFacade#handleAttach}), nên hướng đi đã gửi cho entity cũ không còn hiệu lực. Quên
     * nó đi để nhịp kế tiếp gửi lại nếu người chơi vẫn đang giữ phím — không thì client tự đi một
     * mình (dự đoán cục bộ vẫn chạy) trong khi nhân vật thật đứng yên ở điểm vào, và bị giật ngược
     * về đó lúc nhả phím.
     */
    void onWorldChanged() {
        lastSentDirX = 0f;
        autoWalk.stop(); // ADR-206 — đích là toạ độ world chính, World mới không còn nghĩa
        // ADR-073 D4: entity mới không mang hiệu ứng nào, và server chỉ báo khi ĐỔI so với mặc định của entity đó —
        // choáng dở ở World cũ sẽ không bao giờ được báo "hết".
        onMovementModifier(1f, false);
    }

    /** ADR-070 D4 — server vừa báo tốc độ phi kiếm (phần trăm cộng thêm). */
    void onMoveSpeedPercent(int percent) {
        speedMultiplier = 1f + Math.max(0, percent) / 100f;
    }

    /**
     * ADR-073 — server vừa báo hiệu ứng trên chính mình đổi tốc độ đi / làm không đi được. Hướng đang giữ vẫn được
     * gửi như cũ: server giữ ý định để hết choáng là chạy tiếp.
     */
    void onMovementModifier(float statusMultiplier, boolean cannotMove) {
        statusSpeedMultiplier = Math.max(0f, statusMultiplier);
        immobilized = cannotMove;
    }

    /** Gọi mỗi tick cố định từ {@code WorldScreen#fixedUpdate}. */
    void tick(float fixedDeltaTime) {
        updateMovement();
        updateJump(fixedDeltaTime);
        updateAttack(fixedDeltaTime);
        updateSkill();
        updateArtifact();
    }

    /**
     * ADR-133 — nối hành động phím pháp bảo (một lần, lúc dựng màn hình).
     *
     * @return chính {@code action}, để nút HUD nối cùng hành động trong một dòng ({@code WorldScreen} ở trần PMD)
     */
    Runnable setOnArtifact(Runnable action) {
        this.onArtifact = action;
        return action;
    }

    /** ADR-213 — mục tiêu đang nhắm (0 = không có). */
    long currentTargetId() {
        return context.combat().currentTargetId();
    }

    /**
     * ADR-213 — nhắm {@code targetId} (quái đang theo dõi) như khi click vào nó; đổi được thì gửi lệnh chọn mục tiêu.
     *
     * @return {@code true} nếu giờ đang nhắm đúng {@code targetId}
     */
    boolean focusTarget(long targetId) {
        if (context.combat().selectTarget(targetId)) {
            context.sender().setTarget(targetId);
        }
        return context.combat().currentTargetId() == targetId;
    }

    /** ADR-206 — nối đích tự đi (một lần, lúc dựng màn hình). */
    void useAutoWalk(AutoWalkTarget target) {
        this.autoWalk = target;
    }

    private void updateArtifact() {
        boolean freshPress = artifactKey.update(
                context.inputMapper().isPressed(GameAction.USE_ARTIFACT),
                context.keyboardFree().getAsBoolean());
        if (freshPress && !context.dialogueOpen().getAsBoolean()) {
            onArtifact.run();
        }
    }

    /**
     * ADR-033 — chỉ còn trái/phải (bỏ {@code MOVE_UP}/{@code MOVE_DOWN}, xem {@link GameAction}).
     * {@code dirY} LUÔN gửi {@code 0f}: field còn tồn tại trên dây (movement.proto không xoá field
     * đang dùng, shared/CLAUDE.md) nhưng server-world giờ bỏ qua nó
     * ({@code WorldFacade.handleMoveIntent}) — giữ 0 cục bộ để dự đoán khớp đúng những gì server
     * THẬT SỰ áp dụng.
     *
     * <p>{@code dirX} chỉ có thể là -1/0/1 (hai phím loại trừ nhau) nên không cần chuẩn hoá độ dài
     * như trước ADR-033, khi còn hai trục và đi chéo phải chia cho căn 2.
     */
    private void updateMovement() {
        float dirX = 0f;
        if (isPressed(GameAction.MOVE_LEFT)) {
            dirX -= 1f;
        }
        if (isPressed(GameAction.MOVE_RIGHT)) {
            dirX += 1f;
        }
        dirX = autoWalkDirection(dirX);
        // Quay mặt theo chiều đi; dirX == 0 (đứng yên) GIỮ NGUYÊN hướng cũ, không quay về mặc định.
        if (dirX != 0f) {
            context.models().setFacingRight(WorldConstants.LOCAL_CHARACTER_ID, dirX > 0f);
        }

        VelocityComponent velocity = VELOCITY.get(context.localPlayer());
        velocity.x = immobilized ? 0f : dirX * WorldConstants.MOVE_SPEED_MPS * speedMultiplier * statusSpeedMultiplier;
        velocity.y = 0f;

        if (dirX != lastSentDirX) {
            lastSentDirX = dirX;
            context.sender().moveDirection(dirX);
        }
    }

    /**
     * ADR-206 — người chơi tự bấm phím đi thì huỷ tự đi và theo phím; không thì lái về phía đích, dừng khi tới nơi hoặc
     * khi {@link WorldConstants#AUTO_WALK_STALL_TICKS} tick liền không nhích được (vướng địa hình) — đứng im vì choáng
     * không tính.
     */
    private float autoWalkDirection(float keyDirX) {
        float x = POSITION.get(context.localPlayer()).x;
        autoWalk.trackPosition(x);
        if (!autoWalk.isActive()) {
            return keyDirX;
        }
        if (keyDirX != 0f) {
            autoWalk.stop();
            return keyDirX;
        }
        float dx = autoWalk.targetX() - x;
        if (Math.abs(dx) <= AutoWalkTarget.ARRIVE_DISTANCE_METERS) {
            autoWalk.arrive();
            return 0f;
        }
        if (immobilized || Math.abs(x - autoWalkCheckX) > WorldConstants.AUTO_WALK_PROGRESS_METERS) {
            autoWalkCheckX = x;
            autoWalkStalledTicks = 0;
        } else if (++autoWalkStalledTicks >= WorldConstants.AUTO_WALK_STALL_TICKS) {
            autoWalkStalledTicks = 0;
            autoWalk.stop();
            return 0f;
        }
        return Math.signum(dx);
    }

    private void updateJump(float fixedDeltaTime) {
        boolean jumpPressed = isPressed(GameAction.JUMP);
        if (jumpInput.update(jumpPressed, POSITION.get(context.localPlayer()), fixedDeltaTime)) {
            context.sender().jump();
        }
    }

    private void updateAttack(float fixedDeltaTime) {
        // Đang nói chuyện thì phím Z/Enter thuộc về khung thoại, không phải về combat: người chơi
        // đang đọc chữ, không nhắm ai. Vẫn cho đi lại/nhảy — đứng hình khi mở thoại còn khó chịu hơn.
        if (context.dialogueOpen().getAsBoolean()) {
            attackInput.cancelRetry();
            return;
        }
        // Đang chết thì thôi gửi lại: cửa sổ chờ xác nhận là cho ý định đánh, chết là hết ý định đó.
        // Lần gửi do người chơi vẫn đi như cũ — server mới quyết người chết đánh được hay không.
        if (context.combat().playerHp() <= 0) {
            attackInput.cancelRetry();
        }
        AttackInputController.Send send = attackInput.update(
                isPressed(GameAction.ATTACK), fixedDeltaTime, context.combat().currentTargetId());
        if (send == AttackInputController.Send.NONE) {
            return;
        }
        if (send == AttackInputController.Send.RETRY) {
            // Gửi lại IM LẶNG: chữ nổi/animation đã có ở lần gửi do người chơi, lặp lại mỗi 100ms
            // chỉ làm rối mắt (xem AttackInputController).
            context.sender().attack();
            return;
        }
        // Quái HOẶC nhân vật (đối thủ đấu trường, 2026-10-04 — xem DuelTargeting): trước đây chỉ tra
        // quái, nên trong đấu trường mọi cú bấm đều thành "chưa chọn mục tiêu".
        Optional<PickablePoint> target = context.combat().targetPoint();
        if (target.isEmpty()) {
            context.feedback().showSelfNotice("hud.combat.no_target", FloatingCombatText.MISS);
            return;
        }
        if (!withinAttackRange(target.get())) {
            context.feedback().showSelfNotice("hud.combat.out_of_range", FloatingCombatText.MISS);
        }
        context.models().playAttack(WorldConstants.LOCAL_CHARACTER_ID);
        context.sender().attack();
    }

    private void updateSkill() {
        boolean freshPress = skillKey.update(
                context.inputMapper().isPressed(GameAction.USE_SKILL),
                context.keyboardFree().getAsBoolean());
        if (freshPress && !context.dialogueOpen().getAsBoolean()) {
            onSkillButtonClicked();
        }
    }

    /** Nút Skill trên HUD — cùng đường với phím, nên cả hai đi qua đúng một hàm này. */
    void onSkillButtonClicked() {
        if (!context.combat().useSkill()) {
            return; // Chưa đủ điều kiện cục bộ (hồi chiêu/Khí/mục tiêu) — không gửi gì thừa.
        }
        // Dự đoán CỤC BỘ animation của chính mình. SkillResultEvent xác nhận tới sau sẽ gọi lại
        // models.playSkillCast() lần nữa nhưng vô hại (playOneShotState bỏ qua nếu clip giống hệt
        // đang chạy) — không có xung đột gameplay vì animation không quyết định gì.
        context.models().playSkillCast(WorldConstants.LOCAL_CHARACTER_ID);
        context.sender().useSkill(context.combat().skillCode(), context.combat().currentTargetId());
    }

    private boolean withinAttackRange(PickablePoint target) {
        PositionComponent pos = POSITION.get(context.localPlayer());
        float dx = target.x() - pos.x;
        float dy = target.y() - pos.y;
        // So bình phương — tránh Math.sqrt mỗi lần bấm đánh, và tránh cả sai số của phép căn.
        return (dx * dx) + (dy * dy) <= CombatConstants.ATTACK_RANGE_METERS * CombatConstants.ATTACK_RANGE_METERS;
    }

    /** Phím chỉ là ý định gameplay khi KHÔNG có cửa sổ nào đang chiếm bàn phím — {@link PanelHub}. */
    private boolean isPressed(GameAction action) {
        return context.keyboardFree().getAsBoolean() && context.inputMapper().isPressed(action);
    }

    /**
     * Mọi thứ {@link LocalIntentSender} cần. Gói thành record vì tám tham số rời vượt
     * {@code ParameterNumber} của Checkstyle, cùng lý do {@link PlayContext} tồn tại.
     *
     * @param inputMapper nguồn duy nhất đọc trạng thái phím (CLIENT.md §5)
     * @param sender lối ra duy nhất tới server
     * @param models nhánh render 3D — chỉ để dự đoán animation của CHÍNH mình
     * @param localPlayer entity của chính người chơi, để đọc/ghi vị trí và vận tốc dự đoán
     * @param combat trạng thái HUD combat (mục tiêu, hồi chiêu, Khí) — nguồn của mọi điều kiện cục bộ
     * @param feedback chữ nổi báo lý do khi một ý định không đi tới đâu
     * @param keyboardFree {@code true} khi KHÔNG cửa sổ nào đang nhận chữ gõ vào —
     *     {@link PanelHub#noneOpen()}, nguồn chân lý duy nhất
     * @param dialogueOpen tách riêng khỏi {@code keyboardFree} có chủ đích: đang đọc lời thoại thì
     *     đánh/skill bị chặn nhưng đi lại/nhảy thì KHÔNG (đứng hình khi mở thoại còn khó chịu hơn)
     * @param localCharacterId id THẬT của chính người chơi — nhận ra {@code AttackResultEvent} nào là
     *     xác nhận đòn của MÌNH ({@link AttackInputController#onAttackResult})
     */
    record LocalIntentContext(
            InputMapper inputMapper,
            WorldCommandSender sender,
            WorldModelRenderer models,
            KEntity localPlayer,
            CombatHudController combat,
            CombatFeedbackPresenter feedback,
            BooleanSupplier keyboardFree,
            BooleanSupplier dialogueOpen,
            long localCharacterId) {}
}
