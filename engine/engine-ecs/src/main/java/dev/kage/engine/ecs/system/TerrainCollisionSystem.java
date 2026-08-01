package dev.kage.engine.ecs.system;

import java.util.List;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;

/**
 * Chặn entity đi vào vùng {@link Aabb} tĩnh (PHYSICS.md §2-§3 "va chạm địa hình" — Điều 15, chống
 * hack xuyên tường). Chạy SAU {@link MovementSystem} trong cùng tick (PHYSICS.md §4 thứ tự).
 *
 * <p>Danh sách obstacle mặc định RỖNG — hiện chưa có map/asset thật nào định nghĩa tường (ADR-021:
 * {@code engine-physics} chưa có 1 dòng code, chưa có khái niệm map trong repo). World nào có map
 * thật gọi {@link #setObstacles} khi dữ liệu đó tồn tại; tới lúc đó hệ thống này là no-op an toàn,
 * không đổi hành vi hiện có.
 *
 * <p>Coi entity là **1 điểm**, không bán kính va chạm — kích thước hitbox nhân vật thật phụ thuộc
 * art chưa tồn tại (ADR-021 Revisit Trigger sẽ xét lại khi có). Khi va chạm, lùi thẳng về vị trí
 * tick trước ({@code prevX/prevY}, đã có sẵn cho render interpolation — RENDERING.md §7) thay vì
 * trượt dọc tường — đơn giản nhất (Điều 9), đủ chặn xuyên tường; trượt mượt để sau, đo UX thật
 * trước khi làm (Điều 30).
 */
public final class TerrainCollisionSystem extends IteratingKSystem {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);

    private List<Aabb> obstacles = List.of();

    public TerrainCollisionSystem() {
        super(Family.all(PositionComponent.class).get());
    }

    /**
     * Thay toàn bộ obstacle hiện tại của World này. Copy phòng thủ (Điều 43) — người gọi có thể
     * tái sử dụng/sửa list gốc sau khi gọi mà không ảnh hưởng System. Gọi ngoài tick thread lúc
     * dựng World (chưa có nguồn map thật gọi hàm này — để sẵn cơ chế, ADR-021).
     */
    public void setObstacles(List<Aabb> obstacles) {
        this.obstacles = List.copyOf(obstacles);
    }

    @Override
    protected void processEntity(KEntity entity, float deltaTime) {
        if (obstacles.isEmpty()) {
            return;
        }
        PositionComponent position = POSITION.get(entity);
        // Index loop (không for-each) — tránh Iterator allocation trong game loop (Điều 31).
        for (int i = 0; i < obstacles.size(); i++) {
            if (obstacles.get(i).contains(position.x, position.y)) {
                position.x = position.prevX;
                position.y = position.prevY;
                return;
            }
        }
    }
}
