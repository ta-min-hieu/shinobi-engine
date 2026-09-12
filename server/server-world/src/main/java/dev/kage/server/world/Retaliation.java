package dev.kage.server.world;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.KEntity;
import dev.kage.server.world.component.AiParamsComponent;
import dev.kage.server.world.component.AiState;
import dev.kage.server.world.component.AiStateComponent;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.TargetComponent;

/**
 * ADR-049 — con quái khai {@code retaliates} thì BỊ ĐÁNH là đuổi theo kẻ vừa đánh, kể cả khi
 * {@code aggroRadiusMeters == 0}.
 *
 * <p><b>Vì sao tồn tại.</b> Trước ADR-049, {@code AiSystem} là nơi DUY NHẤT đặt
 * {@link TargetComponent} — qua vòng quét aggro. Nên {@code aggro = 0} không chỉ nghĩa là "không
 * chủ động gây hấn" mà là <b>không bao giờ tự vệ</b>: playtest quest 9 (2026-09-12) đo được boss cao
 * trào arc 2 đứng im chịu 5 nhát, người chơi còn nguyên 118/118, và trọn script hai pha của nó chưa
 * chạy một dòng. Đây là đường thứ HAI đặt mục tiêu, và là lý do phải nhớ rằng {@code AiSystem} không
 * còn độc quyền chuyện đó (AI.md §3).
 *
 * <p><b>Vì sao là lớp riêng chứ không nhét vào một trong hai System.</b> ADR-049 D5 bắt áp cho CẢ
 * đòn thường ({@code CombatSystem}) lẫn đòn kỹ năng ({@code SkillSystem}) — một con boss chỉ đánh
 * trả khi bị đánh thường còn đứng im khi ăn kỹ năng là luật chơi không ai đoán được. Để logic ở một
 * chỗ thì hai System chỉ tốn một dòng gọi, và không có cách nào để chúng lệch nhau.
 *
 * <p><b>Chạy trên tick thread</b> như mọi System khác (SERVER.md §3: chỉ một luồng chạm ECS tại một
 * thời điểm), nên ghi {@code TargetComponent} ở đây không tranh chấp với {@code AiSystem}.
 */
final class Retaliation {

    private static final ComponentMapper<AiParamsComponent> AI_PARAMS = ComponentMapper.getFor(AiParamsComponent.class);
    private static final ComponentMapper<AiStateComponent> AI_STATE = ComponentMapper.getFor(AiStateComponent.class);
    private static final ComponentMapper<TargetComponent> TARGET = ComponentMapper.getFor(TargetComponent.class);
    private static final ComponentMapper<CharacterIdComponent> CHARACTER_ID =
            ComponentMapper.getFor(CharacterIdComponent.class);

    private Retaliation() {}

    /**
     * Gọi ngay sau khi một đòn được phân giải nhắm vào {@code target}.
     *
     * <p>⚠️ Gọi cả khi đòn đó HỤT, và đó là chủ đích: tên ADR là "đánh trả khi <i>bị đánh</i>", và
     * một con boss phớt lờ nhát mở màn chỉ vì nhát đó trượt là hành vi không ai đoán được. Người
     * chơi trúng 95% nên khác biệt này hiếm khi thấy được — nhưng khi thấy thì nó thấy đúng.
     *
     * <p>Bốn cửa thoát, theo thứ tự rẻ dần:
     *
     * <ul>
     *   <li>{@code target} không phải quái (không có {@link AiParamsComponent}) — nhân vật đánh
     *       nhau với nhau là chuyện của PvP, không phải của FSM;
     *   <li>loài không khai {@code retaliates} — mặc định, giữ nguyên ADR-039 D4 cho cọc tập;
     *   <li>nó ĐÃ có mục tiêu — không cướp mục tiêu giữa trận, nếu không hai người chơi thay nhau
     *       đánh sẽ làm con quái quay như chong chóng và không bao giờ ra đòn;
     *   <li>kẻ tấn công không phải người chơi — ADR-049 Revisit Trigger cố ý để ngỏ chuyện quái
     *       đánh nhau.
     * </ul>
     *
     * @param target bên vừa ăn đòn
     * @param attacker bên vừa ra đòn
     */
    static void onAttacked(KEntity target, KEntity attacker) {
        if (target == null || attacker == null || !AI_PARAMS.has(target)) {
            return;
        }
        if (!AI_PARAMS.get(target).retaliates()) {
            return;
        }
        if (!TARGET.has(target) || !AI_STATE.has(target)) {
            return; // quái dựng thiếu component (chỉ xảy ra ở test dựng tay) — bỏ qua an toàn.
        }
        TargetComponent targetComponent = TARGET.get(target);
        if (targetComponent.targetEntityId != 0L) {
            return;
        }
        if (!CHARACTER_ID.has(attacker)) {
            return;
        }
        targetComponent.targetEntityId = CHARACTER_ID.get(attacker).characterId;
        AiStateComponent aiState = AI_STATE.get(target);
        aiState.state = AiState.CHASE;
        aiState.stateTimer = 0f;
    }
}
