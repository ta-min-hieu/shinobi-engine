package dev.kage.engine.ecs;

/**
 * Marker interface cho mọi ECS Component của Kage.
 *
 * <p><b>Bọc khái niệm, không copy API Ashley</b> (ECS.md §10): interface này extends
 * {@code com.badlogic.ashley.core.Component} nên tương thích trực tiếp với Ashley (Entity.add,
 * Family, ComponentMapper đều nhận được), nhưng code gameplay (server-world, client-core, mọi
 * feature) chỉ cần {@code implements dev.kage.engine.ecs.Component} — bytecode của chúng không
 * bao giờ tham chiếu trực tiếp {@code com.badlogic.ashley.core.Component}. Đây là file DUY NHẤT
 * làm cầu nối; nếu thay ECS khác, chỉ sửa ở đây.
 *
 * <p>Component chỉ chứa DỮ LIỆU, mutable + field public là ngoại lệ có chủ đích của
 * Điều 43 — xem CODING-STANDARDS.md §12 và ECS.md §3.1.
 */
public interface Component extends com.badlogic.ashley.core.Component {}
