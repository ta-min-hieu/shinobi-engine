package dev.kage.engine.ecs.component;

import java.util.HashSet;
import java.util.Set;

import dev.kage.engine.ecs.Component;

/**
 * ID các {@code TriggerZoneSystem.NamedZone} entity này đang đứng trong đó, tick trước. Chỉ entity
 * CÓ component này mới được {@code TriggerZoneSystem} theo dõi (opt-in — không phải mọi entity đều
 * cần biết "đang ở vùng nào", vd. quái/NPC tĩnh thường không cần).
 *
 * <p>Mutable + field public: ngoại lệ có chủ đích của Điều 43 (ECS.md §3.1), giống
 * {@link PositionComponent}. {@code TriggerZoneSystem} tự thêm/bớt phần tử — không ai khác nên ghi
 * trực tiếp field này.
 */
public final class TriggerZoneMembershipComponent implements Component {
    public final Set<String> occupiedZoneIds = new HashSet<>();
}
