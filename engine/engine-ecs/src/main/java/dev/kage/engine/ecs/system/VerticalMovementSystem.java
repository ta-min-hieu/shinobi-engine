package dev.kage.engine.ecs.system;

import java.util.List;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;

/**
 * Trọng lực + đáp đất theo trục {@code z} (ADR-033 — nhảy thật kiểu Ninja School Online). Chạy SAU
 * {@link MovementSystem} trong cùng tick (cùng thứ tự "áp chuyển động trước, xử va chạm sau" của
 * {@link TerrainCollisionSystem}, PHYSICS.md §4).
 *
 * <p>KHÔNG đọc hằng số gravity từ đây — {@code gravityMps2} truyền qua constructor (engine/CLAUDE.md:
 * "constructor injection tường minh", cùng lý do {@code MovementSystem} không biết
 * {@code MOVE_SPEED_MPS}: hằng số gameplay cụ thể thuộc về {@code server-world}/{@code client-core}
 * WorldConstants, engine chỉ biết CÁCH tích luỹ). Lực nhảy (vận tốc ban đầu khi bật nhảy) áp trực
 * tiếp vào {@code vz} ở lớp gọi (WorldFacade.handleJumpIntent) — hệ thống này không có khái niệm
 * "nhảy", chỉ có "đang rơi/đang đứng yên theo trục dọc".
 *
 * <p>Danh sách platform mặc định RỖNG — xem {@link Platform}. Mặt đất phẳng {@code z=0} luôn là bệ
 * ngầm định (không cần khai báo), nên hệ thống này AN TOÀN khi chưa có map thật: mọi entity rơi về
 * đúng {@code z=0} rồi grounded, giống hành vi trước ADR-033 (luôn ở {@code z=0}).
 */
public final class VerticalMovementSystem extends IteratingKSystem {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);

    /** Mặt đất phẳng ngầm định khi không platform nào phủ {@code (x, y)} — world chưa có map thật. */
    private static final float GROUND_Z = 0f;

    private final float gravityMps2;
    private List<Platform> platforms = List.of();

    public VerticalMovementSystem(float gravityMps2) {
        super(Family.all(PositionComponent.class).get());
        this.gravityMps2 = gravityMps2;
    }

    /**
     * Thay toàn bộ platform hiện tại của World này. Copy phòng thủ (Điều 43), cùng pattern
     * {@link TerrainCollisionSystem#setObstacles}. Chưa có nguồn map thật gọi hàm này (ADR-033 #7).
     */
    public void setPlatforms(List<Platform> platforms) {
        this.platforms = List.copyOf(platforms);
    }

    @Override
    protected void processEntity(KEntity entity, float deltaTime) {
        PositionComponent pos = POSITION.get(entity);
        pos.prevZ = pos.z;

        if (pos.grounded && pos.vz == 0f) {
            // Đang đứng yên theo trục dọc — nhưng PHẢI kiểm tra mặt đỡ ngay dưới vị trí (x, y) HIỆN
            // TẠI, không được thoát sớm vô điều kiện: entity đi ngang (MovementSystem chạy TRƯỚC hệ
            // này trong cùng tick) có thể vừa rời khỏi mép bệ. Thoát sớm ở đó = đi ra khỏi bệ vẫn
            // lơ lửng giữa không trung mãi mãi, vì không tick nào bật lại được trọng lực.
            if (pos.z <= groundSurfaceBelow(pos.x, pos.y, pos.z)) {
                return; // Vẫn có bệ/mặt đất đỡ — đường tắt thật sự (đa số entity mỗi tick).
            }
            pos.grounded = false; // Vừa rời mép bệ — bắt đầu rơi ngay tick này.
        }

        pos.vz -= gravityMps2 * deltaTime;
        pos.z += pos.vz * deltaTime;

        // Dùng prevZ (độ cao ĐẦU tick) chứ không phải z mới: entity rơi nhanh có thể vượt qua cả
        // mặt bệ trong 1 tick, xét theo prevZ mới bắt được bệ nó đã "xuyên" qua (chống tunnel).
        float surface = groundSurfaceBelow(pos.x, pos.y, pos.prevZ);
        if (pos.z <= surface) {
            pos.z = surface;
            pos.vz = 0f;
            pos.grounded = true;
            // Chạm đất = xoá hạn mức nhảy giữa không trung (xem PositionComponent#airJumpsUsed).
            // Đặt ở đây chứ không ở lớp gọi vì đây là NƠI DUY NHẤT biết chính xác khoảnh khắc đáp
            // đất — client và server dùng chung hệ này nên hạn mức luôn reset giống hệt nhau, không
            // cần đồng bộ thêm field nào qua mạng.
            pos.airJumpsUsed = 0;
        } else {
            pos.grounded = false;
        }
    }

    /**
     * Mặt bệ CAO NHẤT phủ {@code (x, y)} mà entity có thể đã đứng trên/rơi xuyên qua trong tick
     * này ({@code topZ <= fromZ}, tức entity ở trên hoặc ngang mặt bệ ở đầu tick) — trả
     * {@link #GROUND_Z} nếu không platform nào phủ, giữ đúng hành vi mặt đất phẳng khi chưa có map.
     */
    private float groundSurfaceBelow(float x, float y, float fromZ) {
        float best = GROUND_Z;
        // Index loop (không for-each) — tránh Iterator allocation trong game loop (Điều 31).
        for (int i = 0; i < platforms.size(); i++) {
            Platform platform = platforms.get(i);
            if (platform.topZ() <= fromZ && platform.topZ() > best && platform.contains(x, y)) {
                best = platform.topZ();
            }
        }
        return best;
    }
}
