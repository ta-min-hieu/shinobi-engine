package dev.kage.server.world.component;

/** Trạng thái FSM quái thường (AI.md §3). Không có {@code DEAD} — quái chết bị xoá entity ngay. */
public enum AiState {
    IDLE,
    PATROL,
    CHASE,
    ATTACK,
    RETURN
}
