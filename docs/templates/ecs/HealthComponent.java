// TEMPLATE — ECS Component. NGOẠI LỆ của Điều 43: mutable + field public là ĐÚNG ở đây
// (tránh allocation mỗi tick — [ECS §3.1]). Chỉ áp dụng cho Component, không cho DTO/VO.
package dev.kage.engine.ecs.component;

import com.badlogic.ashley.core.Component;   // chỉ engine-ecs được import ashley (ADR-006)

/**
 * Máu của một entity. Component = DỮ LIỆU THUẦN, không logic gameplay.
 *
 * <p>Field public + mutable: đây là "struct", được System ghi trực tiếp trong hot path.
 * Được có method THUẦN đọc chính nó (không side effect) — xem isDead().
 */
public final class HealthComponent implements Component {

    public int current;
    public int max;

    // ✅ OK: method thuần chỉ đọc dữ liệu của chính component
    public boolean isDead() {
        return current <= 0;
    }

    public float percentage() {
        return max <= 0 ? 0f : (float) current / max;
    }

    // ❌ KHÔNG đặt ở đây: attack(), gọi service, truy cập World, business logic → thuộc System.
}
