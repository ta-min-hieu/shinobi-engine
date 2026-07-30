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
    // 50ms, nhưng AreaOfInterestTracker.sync() so cặp entity nên tần suất ảnh hưởng trực tiếp tới
    // CPU. Load test thật N=1000/2000 (Phase 9, 2026-07) cho thấy đây là điểm nghẽn CPU chính —
    // tick p99 vượt ngân sách 16.6ms đã ở N=1000. 20Hz theo yêu cầu chủ dự án (chủ dự án: "10-20
    // lần 1 giây là quá nhiều rồi" cho việc phát hiện vào/ra tầm nhìn).
    //
    // ⚠️ Tách tần suất KHÔNG đủ MỘT MÌNH (đo thật 2026-07-28): N=1000 vẫn 67-83ms tick p99, N=2000
    // SẬP hẳn (tick tối đa 10.76s, lỗi client leo thang không giới hạn). Xem AreaOfInterestTracker
    // javadoc (spatial-grid sharding, giảm còn 56-58ms/2.08-2.55s — vẫn chưa đạt ngân sách vì kịch
    // bản load test tụ 1 điểm, sharding không giúp khi cả N ở chung 1 ô lưới) + BENCHMARK.md §5.2.
    // ✅ Giải quyết dứt điểm ở tầng GAMEPLAY (Phase 9 lát cắt 7): server.gateway.MainWorldChannelRegistry
    // giới hạn cứng người/khu vực (20 mặc định, 50 đặc biệt), tự mở khu vực mới khi đạt 80% sức
    // chứa — đảm bảo 1 World KHÔNG BAO GIỜ phải AoI-xử lý quá đông người bất kể tổng N toàn map.
    // Đo lại: N=1000 → 0.82-1.57ms, N=2000 → 0.98-3.15ms (0 lỗi client) — cả 2 trong ngân sách
    // 16.6ms. Chi tiết đầy đủ: BENCHMARK.md §5.3.
    static final float AOI_SYNC_RATE_HZ = 20f;
    static final int AOI_SYNC_INTERVAL_TICKS = Math.round(TICK_RATE / AOI_SYNC_RATE_HZ);

    private WorldConstants() {}
}
