package dev.kage.server.world;

import java.util.function.LongFunction;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.KEntity;
import dev.kage.server.world.component.AiState;
import dev.kage.server.world.component.AiStateComponent;
import dev.kage.server.world.component.BossAiComponent;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.TargetComponent;

/**
 * Boss mất mục tiêu thì nhắm NGƯỜI KHÁC ĐANG ĐÁNH NÓ thay vì về nhà hồi đầy (chủ dự án chốt
 * 2026-10-11, README dòng 37i).
 *
 * <p>Trước luật này, mục tiêu của boss gục (hoặc lùi vào vùng an toàn) là boss quay về điểm spawn và
 * lành đầy máu ({@code AiSystem.tickReturn}, ADR-046 D4) — kể cả khi đồng đội của người đó vẫn đang
 * đứng đánh. Ở hầm ngục, nơi người gục không tự hồi sinh, mỗi lần một thành viên ngã là cả nhóm mất
 * trắng công đánh.
 *
 * <p>KHÔNG phải bảng thù hận: boss chỉ nhớ MỘT người — người gần đây nhất ra đòn vào nó mà không phải
 * mục tiêu hiện tại ({@link AiStateComponent#otherAttackerId}). Xếp hạng theo sát thương là việc của
 * một ADR riêng. Quái thường không dùng luật này: chủ dự án chỉ quyết cho boss.
 *
 * <p>Lớp riêng (không nằm trong {@code AiSystem}/{@link Retaliation}) vì trần độ phức tạp PMD tính
 * cho cả lớp, và {@code AiSystem} đã gần trần.
 */
final class BossRetargeting {

    private static final ComponentMapper<AiStateComponent> AI_STATE = ComponentMapper.getFor(AiStateComponent.class);
    private static final ComponentMapper<TargetComponent> TARGET = ComponentMapper.getFor(TargetComponent.class);
    private static final ComponentMapper<BossAiComponent> BOSS_AI = ComponentMapper.getFor(BossAiComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<CharacterIdComponent> CHARACTER_ID =
            ComponentMapper.getFor(CharacterIdComponent.class);

    private BossRetargeting() {}

    /**
     * Gọi ngay sau khi một đòn (kể cả hụt) được phân giải nhắm vào {@code target}: nếu kẻ ra đòn là
     * người chơi KHÁC mục tiêu hiện tại của con boss thì ghi nhớ họ.
     */
    static void rememberOtherAttacker(KEntity target, KEntity attacker) {
        if (target == null || attacker == null || !BOSS_AI.has(target) || !CHARACTER_ID.has(attacker)) {
            return;
        }
        if (!AI_STATE.has(target) || !TARGET.has(target)) {
            return; // boss dựng thiếu component (chỉ xảy ra ở test dựng tay) — bỏ qua an toàn.
        }
        long attackerId = CHARACTER_ID.get(attacker).characterId;
        if (attackerId == TARGET.get(target).targetEntityId) {
            return;
        }
        AiStateComponent ai = AI_STATE.get(target);
        ai.otherAttackerId = attackerId;
        ai.secondsSinceOtherAttack = 0f;
    }

    /**
     * Mục tiêu hiện tại vừa mất (gục, rời World, hoặc được vùng an toàn che): thử chuyển sang người
     * đã ghi nhớ.
     *
     * @param entityLookup tra thực thể theo id — cùng hàm {@code AiSystem} dùng
     * @return {@code true} nếu boss đã có mục tiêu mới và đang đuổi; {@code false} = không còn ai,
     *     caller cho nó về nhà như cũ
     */
    static boolean switchToOtherAttacker(KEntity boss, LongFunction<KEntity> entityLookup) {
        if (!BOSS_AI.has(boss)) {
            return false;
        }
        AiStateComponent ai = AI_STATE.get(boss);
        long candidateId = ai.otherAttackerId;
        boolean recent = ai.secondsSinceOtherAttack <= BossConstants.OTHER_ATTACKER_MEMORY_SECONDS;
        forget(ai);
        if (candidateId == 0L || !recent) {
            return false;
        }
        KEntity candidate = entityLookup.apply(candidateId);
        if (candidate == null || HEALTH.get(candidate).isDead() || AiSystem.isShieldedBySafeZone(candidate)) {
            return false;
        }
        TARGET.get(boss).targetEntityId = candidateId;
        ai.state = AiState.CHASE;
        ai.stateTimer = 0f;
        return true;
    }

    /** Xoá trí nhớ — gọi khi boss quay về nhà: trận sau bắt đầu sạch. */
    static void forget(AiStateComponent ai) {
        ai.otherAttackerId = 0L;
        ai.secondsSinceOtherAttack = Float.MAX_VALUE;
    }
}
