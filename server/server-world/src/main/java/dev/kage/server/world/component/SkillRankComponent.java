package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/** Bậc chiêu theo ô của linh căn hiện tại (ADR-071) — chỉ dữ liệu; {@code SkillSystem} đọc khi tung chiêu. */
public final class SkillRankComponent implements Component {

    /** Chỉ số = ô chiêu (0..3); bậc 1..5. */
    public final int[] ranks = {1, 1, 1, 1};
}
