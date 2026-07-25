package dev.kage.server.world;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.server.world.component.StatusEffect;
import dev.kage.server.world.component.StatusEffectComponent;

/**
 * Đếm ngược {@code remainingSeconds} mỗi slot mỗi tick, dọn slot hết hạn — pattern y hệt
 * {@code SkillSystem.processEntity} (đếm ngược cooldown). {@code StatusEffectType.SHIELD} cũng hết
 * hạn theo thời gian ở đây (fallback cap) NGOÀI việc cạn theo damage ở
 * {@link StatusEffects#absorbWithShield}.
 */
final class StatusEffectSystem extends IteratingKSystem {

    private static final ComponentMapper<StatusEffectComponent> STATUS =
            ComponentMapper.getFor(StatusEffectComponent.class);

    StatusEffectSystem() {
        super(Family.all(StatusEffectComponent.class).get());
    }

    @Override
    protected void processEntity(KEntity entity, float deltaTime) {
        StatusEffect[] slots = STATUS.get(entity).slots;
        for (int i = 0; i < slots.length; i++) {
            StatusEffect effect = slots[i];
            if (effect == null) {
                continue;
            }
            effect.remainingSeconds -= deltaTime;
            if (effect.remainingSeconds <= 0f) {
                slots[i] = null;
            }
        }
    }
}
