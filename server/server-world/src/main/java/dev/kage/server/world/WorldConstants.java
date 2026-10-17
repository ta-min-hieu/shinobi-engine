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

    /**
     * Biên trục X của WORLD CHÍNH (dòng 67(c), chủ dự án duyệt 2026-10-17) — trùng dải nền đất client
     * vẽ ({@code client-core DemoPlatforms.GROUND_MIN_X/GROUND_MAX_X}). Trước đó chỉ client biết dải
     * này; server cho đi mãi về hai phía, và client sửa đổi được thì ra khỏi mọi nội dung, đứng giữa
     * khoảng đen. ⚠️ Nới dải nền client thì nới ở đây CÙNG LÚC — không có gì nối hai nơi ngoài dòng này.
     * Hầm ngục/đấu trường không có biên (không có nền vẽ, không có nội dung để đi lạc khỏi).
     */
    static final float MAIN_WORLD_MIN_X = -55f;

    /** Xem {@link #MAIN_WORLD_MIN_X}. */
    static final float MAIN_WORLD_MAX_X = 210f;

    /** Bán kính Area of Interest — placeholder, đo lại ở Phase 3/9 (SERVER.md §6, §11). */
    static final float AOI_RADIUS_METERS = 15f;

    /**
     * Gia tốc trọng trường trục {@code z} (ADR-033) — giá trị ĐỀ XUẤT để mở khoá lát cắt nhảy đầu
     * tiên, CHƯA đo cảm giác rơi thật (chưa có map pilot có bệ để so sánh — cùng tinh thần
     * {@code MOVE_SPEED_MPS} phía trên). {@code client-core.WorldConstants.GRAVITY_MPS2} PHẢI khớp.
     */
    static final float GRAVITY_MPS2 = 20f;

    /**
     * Vận tốc dọc tức thời khi bật nhảy (ADR-033) — với {@link #GRAVITY_MPS2} ở trên, đỉnh nhảy lý
     * thuyết {@code v²/(2g)} ≈ 1.25m, đủ né đòn tầm thấp mà không cần map pilot mới xác nhận cảm
     * giác. {@code client-core.WorldConstants.JUMP_VELOCITY_MPS} PHẢI khớp (dự đoán cục bộ).
     */
    static final float JUMP_VELOCITY_MPS = 7f;

    /**
     * Số lần được bật nhảy THÊM khi đang lơ lửng, mỗi lần rời mặt đất (nhảy đôi kiểu Ninja School
     * Online — chủ dự án yêu cầu 2026-08-09). Đây là TRẦN do server áp: client tự quyết THỜI ĐIỂM
     * gửi lệnh nhảy lần 2 (giữ nút một lúc), nhưng SỐ LẦN thì server đếm và chặn — không có trần
     * này, một client sửa đổi gửi {@code JumpCommand} liên tục sẽ bay vô hạn (Điều 15).
     */
    static final int MAX_AIR_JUMPS = 1;

    /**
     * Vận tốc dọc của cú nhảy giữa không trung — CAO HƠN {@link #JUMP_VELOCITY_MPS} đúng như chủ dự
     * án mô tả ("nhảy lần 2 cao hơn"). Trần độ cao tổng vẫn hữu hạn: nhảy đất tới đỉnh ≈1.25m, bồi
     * thêm {@code 8²/(2·20)} = 1.6m ⇒ tối đa ≈2.85m nếu bấm đúng đỉnh — vẫn thấp hơn khoảng cách
     * giữa các bậc demo đủ để leo, không biến thành bay tự do.
     * {@code client-core.WorldConstants.AIR_JUMP_VELOCITY_MPS} PHẢI khớp (dự đoán cục bộ).
     */
    static final float AIR_JUMP_VELOCITY_MPS = 8f;

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

    /**
     * Nhịp báo vị trí THẬT của nhân vật ra {@link WorldOutboundSink#onCharacterPositionSampled} —
     * nguồn cho việc lưu vị trí (SERVER.md §8). Client chỉ gửi lệnh khi ĐỔI hướng, nên không có nhịp
     * này thì vị trí gateway nhớ là chỗ bấm phím lần cuối. 1 Hz: lúc ngắt kết nối/tắt server mất
     * nhiều nhất quãng đi của một giây ({@link #MOVE_SPEED_MPS} mét); chưa đo, tinh chỉnh một chỗ.
     */
    static final float POSITION_SAMPLE_RATE_HZ = 1f;

    static final int POSITION_SAMPLE_INTERVAL_TICKS = Math.round(TICK_RATE / POSITION_SAMPLE_RATE_HZ);

    private WorldConstants() {}
}
