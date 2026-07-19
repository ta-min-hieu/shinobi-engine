package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * HP hiện tại/tối đa (GAMEPLAY.md §3 bước 6: "Áp dụng damage → HealthComponent"). Mutable + field
 * public: ngoại lệ có chủ đích của Điều 43 (ECS.md §3.1). {@link #isDead()} là method thuần đọc
 * chính nó, được phép theo /engine/CLAUDE.md.
 */
public final class HealthComponent implements Component {
    public int current;
    public int max;

    public boolean isDead() {
        return current <= 0;
    }
}
