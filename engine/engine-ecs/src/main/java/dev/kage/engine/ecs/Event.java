package dev.kage.engine.ecs;

/**
 * Marker cho Event — sự thật ĐÃ xảy ra, tên ở thì quá khứ: {@code MonsterKilledEvent} (ADR-008).
 *
 * <p>Implementation nên là {@code record} bất biến. Event dùng cho fan-out (1 nguồn → nhiều
 * listener không quan tâm lẫn nhau) — cần kết quả trả về ngay thì gọi Facade trực tiếp, không
 * dùng Event (ECS.md §7, ADR-008).
 */
public interface Event {
}
