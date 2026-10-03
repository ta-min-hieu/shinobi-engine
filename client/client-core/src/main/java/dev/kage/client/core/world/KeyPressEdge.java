package dev.kage.client.core.world;

/**
 * Mép nhấn ("vừa nhấn") của một phím poll được — biến trạng thái giữ/nhả thành đúng MỘT ý định.
 *
 * <p>Điểm khác với một bộ dò mép thường: phím nào đang bị giữ trong lúc một ô nhập chiếm bàn phím
 * thì KHÔNG được tính là cú nhấn mới khi bàn phím được trả lại — phải nhả ra rồi nhấn lại. Lý do
 * thật: Enter vừa là phím GỬI của mọi ô nhập ({@code TextField} nhận {@code '\n'} ngay lúc NHẤN và
 * ô đóng trong cùng khung input) vừa là phím dùng skill. Không có luật này thì nhịp logic ngay sau
 * đó thấy "bàn phím rảnh + Enter đang giữ" và tung skill mỗi lần người chơi gửi một câu chat
 * ({@code KeyPressEdgeTest}).
 *
 * <p>Cái giá: nhấn đúng nhịp đầu tiên sau khi đóng ô nhập bằng ESC thì cú nhấn đó bị bỏ (1 tick,
 * ~16ms) — rẻ hơn hẳn một skill bắn nhầm vào mục tiêu.
 */
final class KeyPressEdge {

    /** Phím đã được coi là "đang giữ" ở nhịp trước — kể cả giữ trong lúc bàn phím bị chiếm. */
    private boolean heldBefore;

    /**
     * Ghi trạng thái phím của nhịp này và cho biết đây có phải một cú nhấn mới thuộc về gameplay.
     *
     * @param held phím đang được giữ hay không (trạng thái thô từ {@code InputMapper})
     * @param keyboardFree {@code false} khi một ô nhập đang chiếm bàn phím
     * @return {@code true} đúng một lần cho mỗi cú nhấn thuộc về gameplay
     */
    boolean update(boolean held, boolean keyboardFree) {
        if (!keyboardFree) {
            // Mọi phím lúc này là của ô nhập — kể cả cú nhấn đóng ô nhập đó. Coi như đang giữ để
            // nó không thành cú nhấn mới khi bàn phím được trả lại.
            heldBefore = true;
            return false;
        }
        boolean edge = held && !heldBefore;
        heldBefore = held;
        return edge;
    }
}
