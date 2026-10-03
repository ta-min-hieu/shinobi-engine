package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Mép nhấn của phím dùng skill (Enter) — đặc biệt là lúc một ô nhập vừa trả bàn phím lại.
 *
 * <p>Tái hiện lỗi thật: Enter vừa GỬI tin chat ({@code TextField} nhận {@code '\n'} ngay lúc NHẤN và
 * ô nhập đóng trong cùng khung input) vừa là phím {@code USE_SKILL}. Nhịp logic ngay sau đó thấy bàn
 * phím đã rảnh mà Enter vẫn đang giữ — bản cũ coi đó là một cú nhấn MỚI và tung skill vào mục tiêu
 * đang chọn mỗi lần người chơi gửi một câu chat.
 */
class KeyPressEdgeTest {

    private final KeyPressEdge edge = new KeyPressEdge();

    @Test
    void shouldFire_onceOnPress_whileKeyboardIsFree() {
        assertThat(edge.update(false, true)).isFalse();
        assertThat(edge.update(true, true)).isTrue();
        assertThat(edge.update(true, true)).as("giữ phím không lặp").isFalse();
        assertThat(edge.update(false, true)).isFalse();
        assertThat(edge.update(true, true)).as("nhả rồi nhấn lại là cú mới").isTrue();
    }

    @Test
    void shouldNotFire_whenEnterThatSubmittedChatIsStillHeld() {
        // Đang gõ chat: bàn phím thuộc ô nhập, Enter chưa nhấn.
        assertThat(edge.update(false, false)).isFalse();
        assertThat(edge.update(false, false)).isFalse();
        // Nhấn Enter: ô nhập gửi tin và đóng NGAY trong khung input → nhịp này bàn phím đã rảnh, Enter
        // vẫn đang giữ. Đây là phím của ô chat, không phải lệnh dùng skill.
        assertThat(edge.update(true, true)).as("Enter vừa gửi chat").isFalse();
        assertThat(edge.update(true, true)).isFalse();
        // Nhả rồi nhấn lại — giờ mới là ý định dùng skill thật.
        assertThat(edge.update(false, true)).isFalse();
        assertThat(edge.update(true, true)).isTrue();
    }

    @Test
    void shouldNotFire_whenKeyIsPressedWhileKeyboardIsTaken() {
        assertThat(edge.update(true, false)).isFalse();
        assertThat(edge.update(true, false)).isFalse();
    }
}
