package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Khí hiện tại/tối đa (GDD §5 "Cơ bản: STR · AGI · INT · VIT · Khí") — trừ khi cast skill
 * (GAMEPLAY.md §3 bước 3 "Đủ Khí không?"). Cùng mức trưởng thành với {@link HealthComponent}:
 * đặt đầy lúc attach, KHÔNG đọc/ghi {@code character_stat.current_chi} DB, không hồi phục (GDD
 * chưa chốt cơ chế hồi Khí) — placeholder, nối DB thật là việc của lát cắt sau.
 */
public final class ChiComponent implements Component {
    public int current;
    public int max;
}
