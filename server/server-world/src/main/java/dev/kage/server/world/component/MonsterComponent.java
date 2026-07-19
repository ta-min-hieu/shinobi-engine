package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Đánh dấu entity là QUÁI (không phải nhân vật người chơi) + mã định danh loại quái
 * (GAMEPLAY.md §5 {@code monster_template.code} — lát cắt này hardcode 1 quái "training_dummy",
 * chưa đọc từ DB).
 */
public final class MonsterComponent implements Component {
    public String monsterCode;
}
