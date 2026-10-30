package dev.kage.server.world;

/** Kết quả chọn cách hồi sinh (ADR-117). */
public enum ReviveOutcome {
    OK,
    /** Không đang nằm chờ (chưa gục, đã tự về điểm hồi sinh, hoặc không ở world chính). */
    NOT_DEAD,
    /** Bị người chơi hạ — không được đứng dậy tại chỗ. */
    FORBIDDEN
}
