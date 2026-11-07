package dev.kage.client.core.world;

/**
 * Hằng số cho {@link WorldScreen} (CODING-STANDARDS §18 — không magic number).
 *
 * <p>⚠️ {@code MOVE_SPEED_MPS} PHẢI khớp {@code server-world.WorldConstants.MOVE_SPEED_MPS} —
 * client chỉ dùng số này để DỰ ĐOÁN cục bộ (CLIENT.md §6), server luôn là nguồn chân lý thật.
 * Lệch giữa 2 số này chỉ gây dự đoán sai (tự sửa qua {@code MoveResponse}), không phải lỗ hổng.
 */
final class WorldConstants {

    /**
     * Id dùng cho nhân vật của CHÍNH người chơi trong {@code WorldModelRenderer}. Server đánh id cho
     * nhân vật khác; nhân vật của mình chưa bao giờ cần id nên không có. 0 an toàn vì id thật sinh
     * từ sequence của DB, luôn dương — đây là sentinel nội bộ cho render, KHÔNG phải id thật.
     */
    static final long LOCAL_CHARACTER_ID = 0L;

    static final float MOVE_SPEED_MPS = 5f;

    /** ADR-206 — tự đi coi là "còn nhích" khi đi được hơn chừng này mét giữa hai lần kiểm. */
    static final float AUTO_WALK_PROGRESS_METERS = 0.1f;

    /** ADR-206 — tự đi bao nhiêu tick liền không nhích được thì bỏ (2 giây ở 60 Hz — vướng địa hình). */
    static final int AUTO_WALK_STALL_TICKS = 120;

    /**
     * ADR-033 — PHẢI khớp {@code server-world.WorldConstants.GRAVITY_MPS2}/{@code
     * JUMP_VELOCITY_MPS}, cùng lý do {@code MOVE_SPEED_MPS}: chỉ dùng để dự đoán cục bộ, lệch chỉ
     * gây sửa lại qua {@code MoveResponse}, không phải lỗ hổng (server luôn là nguồn chân lý).
     */
    static final float GRAVITY_MPS2 = 20f;

    static final float JUMP_VELOCITY_MPS = 7f;

    /**
     * Nhảy đôi (chủ dự án yêu cầu 2026-08-09) — PHẢI khớp
     * {@code server-world.WorldConstants.MAX_AIR_JUMPS}/{@code AIR_JUMP_VELOCITY_MPS}. Client chỉ
     * dùng để dự đoán; server mới là nơi ĐẾM và chặn thật (WorldFacade#handleJumpIntent).
     */
    static final int MAX_AIR_JUMPS = 1;

    static final float AIR_JUMP_VELOCITY_MPS = 8f;

    /**
     * Giữ nút nhảy liên tục bao lâu (giây) thì tự bung cú nhảy thứ hai — đây là CẢM GIÁC ĐIỀU
     * KHIỂN thuần client ("giữ yên nút nhảy một lúc sẽ nhảy lần 2", lời chủ dự án 2026-08-09), KHÔNG
     * có bản sao phía server: server chỉ đếm số lần, không quan tâm nhấn lúc nào.
     *
     * <p>0.3s chọn theo thời gian tới đỉnh của cú nhảy đầu ({@code JUMP_VELOCITY_MPS / GRAVITY_MPS2}
     * = 0.35s) — bung ngay TRƯỚC đỉnh nên cú thứ hai nối tiếp mượt, không phải "khựng lại rồi nhảy".
     * Nhả rồi bấm lại giữa không trung cũng kích hoạt được cú này (phản xạ platformer quen thuộc),
     * xem {@code WorldScreen#updateJumpIntent}.
     */
    static final float AIR_JUMP_HOLD_SECONDS = 0.3f;

    static final float BOX_SIZE = 1f;

    /**
     * Chiều cao khối thay thế của quái (ADR-033) — quái CHƯA có model 3D, vẽ bằng khối đứng cao
     * xấp xỉ nhân vật (~1.8) để nhìn ra là một sinh vật đứng trên đất, không phải vệt phẳng.
     */
    static final float MONSTER_HEIGHT_UNITS = 1.6f;

    static final float VIEWPORT_HEIGHT_UNITS = 10f;

    /**
     * Lệch vị trí (world unit) giữa dự đoán cục bộ và {@code MoveResponse} trước khi coi là
     * "lệch lớn" và snap thẳng theo server (CLIENT.md §6: "lệch nhỏ → lerp mượt, lệch lớn →
     * snap"). Dự đoán cục bộ chạy CÙNG công thức server nên lệch nhỏ tự triệt tiêu ở tick sau —
     * chưa cần lerp riêng ở lát cắt này (follow-up nếu số liệu thật cho thấy cần).
     */
    static final float RECONCILE_SNAP_THRESHOLD = 1f;

    private WorldConstants() {}
}
