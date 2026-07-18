package dev.kage.client.core.input;

/**
 * Ý ĐỊNH của người chơi — gameplay code không biết phím nào được bấm, chỉ biết ý định
 * (CLIENT.md §5). Đổi từ bàn phím sang cảm ứng = đổi ánh xạ ở {@link InputMapper}, không đụng
 * gameplay.
 */
public enum GameAction {
    MOVE_LEFT,
    MOVE_RIGHT,
    MOVE_UP,
    MOVE_DOWN
}
