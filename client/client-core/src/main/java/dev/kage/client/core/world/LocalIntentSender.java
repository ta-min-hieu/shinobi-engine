package dev.kage.client.core.world;

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

    /** Hướng đi gửi lần cuối — chỉ gửi lại khi ĐỔI, không phải mỗi tick (60 gói/giây là vô nghĩa). */
    private float lastSentDirX;

    /**
     * Toàn bộ trạng thái thời gian của nút nhảy (mép nhấn + đồng hồ giữ nút) — {@link GameAction}
     * chỉ nói "đang giữ hay không", biến nó thành cú nhảy đơn/đôi là việc của lớp kia.
     */
    private final JumpInputController jumpInput = new JumpInputController();

    /** Phím Z + nút Đánh trên HUD gộp về một nguồn — xem {@link AttackInputController}. */
    private final AttackInputController attackInput;

    /** Edge-detection cho phím dùng skill — giữ phím không spam (nhảy có bộ đếm riêng ở {@link #jumpInput}). */
    private boolean skillKeyWasPressed;

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
    }

    /** Gọi mỗi tick cố định từ {@code WorldScreen#fixedUpdate}. */
    void tick(float fixedDeltaTime) {
        updateMovement();
        updateJump(fixedDeltaTime);
        updateAttack(fixedDeltaTime);
        updateSkill();
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
        // Quay mặt theo chiều đi; dirX == 0 (đứng yên) GIỮ NGUYÊN hướng cũ, không quay về mặc định.
        if (dirX != 0f) {
            context.models().setFacingRight(WorldConstants.LOCAL_CHARACTER_ID, dirX > 0f);
        }

        VelocityComponent velocity = VELOCITY.get(context.localPlayer());
        velocity.x = dirX * WorldConstants.MOVE_SPEED_MPS;
        velocity.y = 0f;

        if (dirX != lastSentDirX) {
            lastSentDirX = dirX;
            context.sender().moveDirection(dirX);
        }
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
        CombatHudController.MonsterState target = context.combat().targetState();
        if (target == null) {
            context.feedback().showSelfNotice("hud.combat.no_target", FloatingCombatText.MISS);
            return;
        }
        if (!withinAttackRange(target)) {
            context.feedback().showSelfNotice("hud.combat.out_of_range", FloatingCombatText.MISS);
        }
        context.models().playAttack(WorldConstants.LOCAL_CHARACTER_ID);
        context.sender().attack();
    }

    private void updateSkill() {
        boolean skillPressed = isPressed(GameAction.USE_SKILL);
        if (skillPressed && !skillKeyWasPressed && !context.dialogueOpen().getAsBoolean()) {
            onSkillButtonClicked();
        }
        skillKeyWasPressed = skillPressed;
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

    private boolean withinAttackRange(CombatHudController.MonsterState target) {
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
