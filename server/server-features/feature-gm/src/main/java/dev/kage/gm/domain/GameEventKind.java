package dev.kage.gm.domain;

/** Loại sự kiện nhân đôi (ADR-067). Tên enum là giá trị cột {@code game_event.kind}. */
public enum GameEventKind {
    /** Nhân EXP đánh quái (cả phần chia cho đồng đội). Thưởng nhiệm vụ KHÔNG nhân. */
    EXP,
    /** Nhân số lượt roll bảng rơi đồ của mỗi con quái. */
    DROP
}
