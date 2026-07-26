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

    // AoI sync TÁCH khỏi TICK_RATE (ADR-018 §Consequences/§Revisit Trigger: "giữ logic 60Hz, hạ
    // snapshot rate") — người chơi không phân biệt được vị trí người khác cập nhật mỗi 16ms hay
    // 50ms, nhưng AreaOfInterestTracker.sync() là O(n²)/tick nên tần suất ảnh hưởng trực tiếp tới
    // CPU. Load test thật N=1000/2000 (Phase 9, 2026-07) cho thấy đây là điểm nghẽn CPU chính —
    // tick p99 vượt ngân sách 16.6ms đã ở N=1000. 20Hz theo yêu cầu chủ dự án (chủ dự án: "10-20
    // lần 1 giây là quá nhiều rồi" cho việc phát hiện vào/ra tầm nhìn).
    static final float AOI_SYNC_RATE_HZ = 20f;
    static final int AOI_SYNC_INTERVAL_TICKS = Math.round(TICK_RATE / AOI_SYNC_RATE_HZ);

    private WorldConstants() {
    }
}
