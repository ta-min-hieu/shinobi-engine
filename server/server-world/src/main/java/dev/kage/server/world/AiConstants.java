package dev.kage.server.world;

/**
 * Tham số FSM quái (AI.md §3) — TOÀN BỘ số ở đây là PLACEHOLDER, chưa cân bằng qua playtest, cùng
 * tinh thần {@link CombatConstants}. AI.md §10 nói tham số AI nên là config data
 * ({@code monster_template} DB) — chưa có bảng đó (1 quái hardcode, lát cắt sau mới DB-driven),
 * nên tạm đặt hằng số ở đây thay vì rải rác trong {@link AiSystem} (CODING-STANDARDS.md §18).
 */
final class AiConstants {

    static final float AGGRO_RADIUS_METERS = 6f;
    static final float LEASH_RADIUS_METERS = 10f; // quá xa spawn lúc Chase -> bỏ cuộc, Return
    static final float CHASE_SPEED_MPS = 3f;
    static final float PATROL_SPEED_MPS = 1.5f;
    static final float PATROL_RADIUS_METERS = 3f;
    static final float ARRIVAL_THRESHOLD_METERS = 0.3f;
    static final float IDLE_DURATION_SECONDS = 3f;
    static final float MAX_PATROL_DURATION_SECONDS = 5f; // tránh kẹt nếu vì lý do gì không tới được điểm patrol

    private AiConstants() {}
}
