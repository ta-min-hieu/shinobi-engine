package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Gắn {@code monsterId} (ÂM, server-world tự cấp — combat.proto) vào entity ECS của quái — đối
 * xứng với {@link CharacterIdComponent} phía nhân vật. Cần để {@code CombatSystem} biết ID THẬT
 * của attacker khi attacker là quái (không phải nhân vật), vì 2 phe dùng 2 component ID khác nhau.
 */
public final class MonsterIdComponent implements Component {
    public long monsterId;
}
