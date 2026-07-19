package dev.kage.server.world.component;

import dev.kage.engine.ecs.Component;

/**
 * Mục tiêu chiến đấu đã CHỌN (GDD §6 "Auto-target đánh thường") — server tự động đánh khi hết
 * cooldown mà không cần lệnh "đánh" mỗi nhát (xem {@link AttackCooldownComponent}).
 *
 * <p>{@code targetEntityId == 0} nghĩa là CHƯA chọn mục tiêu — an toàn vì characterId (dương, DB
 * auto-increment từ 1) và monster instance ID (âm, server-world tự cấp) đều không bao giờ bằng 0
 * (combat.proto).
 */
public final class TargetComponent implements Component {
    public long targetEntityId;
}
