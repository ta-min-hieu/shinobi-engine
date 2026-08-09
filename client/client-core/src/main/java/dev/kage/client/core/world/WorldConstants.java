package dev.kage.client.core.world;

/**
 * Hằng số cho {@link WorldScreen} (CODING-STANDARDS §18 — không magic number).
 *
 * <p>⚠️ {@code MOVE_SPEED_MPS} PHẢI khớp {@code server-world.WorldConstants.MOVE_SPEED_MPS} —
 * client chỉ dùng số này để DỰ ĐOÁN cục bộ (CLIENT.md §6), server luôn là nguồn chân lý thật.
 * Lệch giữa 2 số này chỉ gây dự đoán sai (tự sửa qua {@code MoveResponse}), không phải lỗ hổng.
 */
final class WorldConstants {

    static final float MOVE_SPEED_MPS = 5f;

    /**
     * ADR-033 — PHẢI khớp {@code server-world.WorldConstants.GRAVITY_MPS2}/{@code
     * JUMP_VELOCITY_MPS}, cùng lý do {@code MOVE_SPEED_MPS}: chỉ dùng để dự đoán cục bộ, lệch chỉ
     * gây sửa lại qua {@code MoveResponse}, không phải lỗ hổng (server luôn là nguồn chân lý).
     */
    static final float GRAVITY_MPS2 = 20f;

    static final float JUMP_VELOCITY_MPS = 7f;

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
