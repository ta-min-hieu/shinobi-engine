package dev.kage.engine.ecs.component;

import dev.kage.engine.ecs.Component;

/**
 * Vị trí entity (world unit = mét — RENDERING.md §4).
 *
 * <p>Mutable + field public: ngoại lệ có chủ đích của Điều 43 (ECS.md §3.1). Lưu cả vị trí tick
 * trước ({@code prevX/prevY}) để client nội suy hình ảnh mượt giữa 2 tick (RENDERING.md §7).
 *
 * <p>{@code z}/{@code prevZ}/{@code vz}/{@code grounded} thêm ở ADR-033 (2026-08-08) — trục đứng
 * THẬT đầu tiên của gameplay (nhảy kiểu Ninja School Online), tích luỹ bởi
 * {@code VerticalMovementSystem}. {@code grounded} mặc định {@code true} (đứng trên đất, KHÔNG
 * phải giá trị mặc định {@code false} của {@code boolean}) — mọi entity mới tạo (nhân vật, quái,
 * NPC) đứng yên trên mặt đất cho tới khi có lực nhảy, không phải rơi tự do ngay lúc spawn.
 */
public final class PositionComponent implements Component {
    public float x;
    public float y;
    public float prevX;
    public float prevY;
    public float z;
    public float prevZ;
    public float vz;
    public boolean grounded = true;

    /**
     * Số lần đã bật nhảy GIỮA KHÔNG TRUNG kể từ lần rời mặt đất gần nhất (nhảy đôi kiểu Ninja
     * School Online — chủ dự án yêu cầu 2026-08-09, xem ADR-033). Chỉ có ý nghĩa khi
     * {@code grounded == false}; {@code VerticalMovementSystem} tự đặt về 0 mỗi lần chạm đất, nên
     * người đọc field này không cần tự dọn.
     *
     * <p>Đây là DỮ LIỆU trạng thái dọc như {@code vz}/{@code grounded}, không phải luật chơi:
     * "được nhảy thêm mấy lần" là hằng số gameplay nằm ở {@code WorldConstants} của
     * {@code server-world}/{@code client-core}, engine không biết ngưỡng đó (cùng lý do
     * {@code VerticalMovementSystem} nhận gravity qua constructor).
     */
    public int airJumpsUsed;
}
