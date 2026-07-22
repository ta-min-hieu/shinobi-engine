package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Thời gian còn lại (giây) trước lần dùng skill tiếp theo — MỘT giá trị dùng chung cho skill duy
 * nhất ở lát cắt này ({@code SkillConstants.FIREBALL_SKILL_CODE}); cooldown riêng từng skill là
 * việc của lát cắt sau khi có {@code skill_template}/{@code character_skill} DB. Server giữ tuyệt
 * đối, client không bao giờ gửi cooldown (SECURITY.md §8 — giống {@link AttackCooldownComponent}).
 */
public final class SkillCooldownComponent implements Component {
    public float remainingSeconds;
}
