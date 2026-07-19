package dev.kage.server.world;

/**
 * Hằng số world tick (SERVER.md §3-4, §6). {@code TICK_RATE}/{@code FIXED_DT} PHẢI khớp
 * {@code KageEngine.TICK_RATE} phía client (ADR-018) — không import {@code engine-core} được
 * (engine-ecs không phụ thuộc engine-core, ENGINE.md §9) nên định nghĩa lại độc lập ở đây.
 *
 * <p>⚠️ {@code MOVE_SPEED_MPS} và {@code AOI_RADIUS_METERS} là giá trị ĐỀ XUẤT để mở khoá lát
 * cắt movement đầu tiên — CHƯA đo (SERVER.md §6: "Chưa chốt kích thước AoI... Cần đo ở Phase 3").
 * Không coi đây là số liệu cuối cùng, chỉ tinh chỉnh 1 chỗ khi có số liệu thật.
 */
final class WorldConstants {

    static final float TICK_RATE = 60f;
    static final float FIXED_DT = 1f / TICK_RATE;

    /** Tốc độ di chuyển server SỞ HỮU — client chỉ gửi hướng, không gửi tốc độ (movement.proto). */
    static final float MOVE_SPEED_MPS = 5f;

    /** Bán kính Area of Interest — placeholder, đo lại ở Phase 3/9 (SERVER.md §6, §11). */
    static final float AOI_RADIUS_METERS = 15f;

    private WorldConstants() {
    }
}
