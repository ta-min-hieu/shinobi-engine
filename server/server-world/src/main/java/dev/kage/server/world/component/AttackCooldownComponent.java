package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Thời gian còn lại (giây) trước nhát đánh thường tiếp theo — server giữ tuyệt đối, client không
 * bao giờ gửi cooldown (SECURITY.md §8 "Cooldown hack" — chặn bằng thiết kế, không phải kiểm tra
 * hậu kỳ, giống {@code MoveCommand} chỉ gửi hướng chứ không gửi tốc độ).
 */
public final class AttackCooldownComponent implements Component {
    public float remainingSeconds;
}
