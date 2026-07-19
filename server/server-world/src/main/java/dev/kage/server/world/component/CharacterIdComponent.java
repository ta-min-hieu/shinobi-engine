package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Gắn {@code characterId} (feature-character) vào entity ECS phía server-world — cần để
 * {@code AreaOfInterestTracker} và các System khác biết entity này thuộc nhân vật nào khi lặp
 * qua {@code EcsWorld} (KEntity.id() là ID nội bộ world, không phải characterId nghiệp vụ).
 *
 * <p>Mutable + field public: ngoại lệ có chủ đích của Điều 43 (ECS.md §3.1).
 */
public final class CharacterIdComponent implements Component {
    public long characterId;
}
