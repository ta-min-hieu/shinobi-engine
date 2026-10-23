package dev.kage.server.world;

import java.util.ArrayList;
import java.util.List;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.server.world.component.CharacterIdComponent;
import dev.kage.server.world.component.HealthComponent;
import dev.kage.server.world.component.MoveIntentComponent;
import dev.kage.server.world.component.StatusEffectComponent;

/**
 * Tính lại vận tốc ngang của NHÂN VẬT mỗi tick từ hướng đang giữ ({@link MoveIntentComponent}) và
 * hiệu ứng trạng thái HIỆN HÀNH — chạy ngay TRƯỚC {@code MovementSystem}.
 *
 * <p>Sửa 2026-10-02: trước đây vận tốc chỉ được tính lúc nhận {@code MoveCommand}, mà client chỉ gửi
 * lệnh đó khi HƯỚNG đổi. Bị stun giữa lúc giữ phím thì chạy tiếp nguyên tốc độ; slow trúng giữa lúc
 * chạy thì không chậm lại; buff tăng tốc hết hạn thì tốc độ được buff giữ mãi tới lần đổi hướng sau.
 * Quái không dính vì {@code AiSystem} vốn đã tính lại vận tốc mỗi tick — đây là phần tương ứng cho
 * người chơi. Không log (/CLAUDE.md §6).
 *
 * <p>ADR-073: khi hệ số tốc độ do hiệu ứng hoặc trạng thái "không đi được" ĐỔI so với lần báo trước, ghi một
 * {@link MovementModifierChange} để World báo cho chính nhân vật đó (client dự đoán đúng, hết giật vị trí). Chỉ cấp
 * phát lúc đổi (trúng/hết hiệu ứng), không phải mỗi tick.
 */
final class CharacterLocomotionSystem extends IteratingKSystem {

    private static final ComponentMapper<MoveIntentComponent> MOVE_INTENT =
            ComponentMapper.getFor(MoveIntentComponent.class);
    private static final ComponentMapper<VelocityComponent> VELOCITY = ComponentMapper.getFor(VelocityComponent.class);
    private static final ComponentMapper<StatusEffectComponent> STATUS_EFFECT =
            ComponentMapper.getFor(StatusEffectComponent.class);
    private static final ComponentMapper<HealthComponent> HEALTH = ComponentMapper.getFor(HealthComponent.class);
    private static final ComponentMapper<CharacterIdComponent> CHARACTER_ID =
            ComponentMapper.getFor(CharacterIdComponent.class);

    private final List<MovementModifierChange> pendingChanges = new ArrayList<>();

    CharacterLocomotionSystem() {
        super(Family.all(MoveIntentComponent.class, VelocityComponent.class, StatusEffectComponent.class)
                .get());
    }

    @Override
    protected void processEntity(KEntity entity, float deltaTime) {
        VelocityComponent velocity = VELOCITY.get(entity);
        StatusEffectComponent status = STATUS_EFFECT.get(entity);
        HealthComponent health = HEALTH.get(entity);
        boolean incapacitated = StatusEffects.isStunned(status) || (health != null && health.isDead());
        MoveIntentComponent intent = MOVE_INTENT.get(entity);
        float speed =
                WorldConstants.MOVE_SPEED_MPS * intent.speedMultiplier * StatusEffects.moveSpeedMultiplier(status);
        // ADR-033: người chơi chỉ di chuyển ngang — trục y luôn 0, cùng luật với handleMoveIntent.
        velocity.x = incapacitated ? 0f : intent.dirX * speed;
        velocity.y = 0f;
        announceIfChanged(entity, intent, StatusEffects.moveSpeedMultiplier(status), incapacitated);
    }

    /** Các thay đổi từ lần xả trước — World gửi cho từng nhân vật (ADR-073). */
    List<MovementModifierChange> drainChanges() {
        if (pendingChanges.isEmpty()) {
            return List.of();
        }
        List<MovementModifierChange> changes = List.copyOf(pendingChanges);
        pendingChanges.clear();
        return changes;
    }

    private void announceIfChanged(KEntity entity, MoveIntentComponent intent, float multiplier, boolean immobilized) {
        if (Float.compare(intent.announcedStatusMultiplier, multiplier) == 0
                && intent.announcedImmobilized == immobilized) {
            return;
        }
        intent.announcedStatusMultiplier = multiplier;
        intent.announcedImmobilized = immobilized;
        if (CHARACTER_ID.has(entity)) {
            pendingChanges.add(
                    new MovementModifierChange(CHARACTER_ID.get(entity).characterId, multiplier, immobilized));
        }
    }

    /** Hệ số tốc độ do hiệu ứng + "không đi được" của một nhân vật vừa đổi (ADR-073). */
    record MovementModifierChange(long characterId, float statusSpeedMultiplier, boolean immobilized) {}
}
