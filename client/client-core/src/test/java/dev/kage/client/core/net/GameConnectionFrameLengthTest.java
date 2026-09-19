package dev.kage.client.core.net;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Con số độ dài trên dây không được đi thẳng vào {@code new byte[...]}.
 *
 * <p>Bất đối xứng đo được 2026-09-19: {@code GatewayServer} chặn khung > 1MB bằng
 * {@code LengthFieldBasedFrameDecoder} và có hẳn ô test bảo mật cho nó, còn {@code GameConnection}
 * thì tin con số đó vô điều kiện. Ba kiểu hỏng, không kiểu nào cần một server thù địch — một khung
 * dở dang là đủ:
 *
 * <ul>
 *   <li>quá lớn ⇒ {@code OutOfMemoryError};
 *   <li>ÂM ⇒ {@code NegativeArraySizeException}, mà {@code connectionLoop} chỉ bắt
 *       {@code IOException} ⇒ luồng kết nối chết hẳn, mất luôn cả khả năng thử lại;
 *   <li>lớn vừa nhưng bịa ⇒ {@code readFully} chờ mãi ({@code soTimeout} = 0 sau bắt tay TLS).
 * </ul>
 *
 * <p>Đo thẳng hàm canh cửa thay vì dựng một server giả: harness của {@code client-core} dùng
 * {@code ServerSocket} thuần (xem {@link GameConnectionReconnectTest}) nên bắt tay TLS không bao
 * giờ xong và {@code readLoop} không bao giờ chạy tới. Hàm này là cửa DUY NHẤT giữa dây và phép
 * cấp phát, nên đo nó là đo đúng chỗ.
 */
class GameConnectionFrameLengthTest {

    private static final int MAX = 1024 * 1024;

    @Test
    void shouldRejectNegativeLength_insteadOfKillingTheConnectionThread() {
        // -1 là thứ readInt() trả về khi byte trên dây là 0xFFFFFFFF. Trước bản vá nó thành
        // NegativeArraySizeException — KHÔNG phải IOException, nên nó xuyên qua connectionLoop.
        assertThatThrownBy(() -> GameConnection.checkedFrameLength(-1))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("-1");
    }

    @Test
    void shouldRejectHugeLength_insteadOfAllocatingIt() {
        assertThatThrownBy(() -> GameConnection.checkedFrameLength(Integer.MAX_VALUE))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> GameConnection.checkedFrameLength(MAX + 1)).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectLengthTooShortToHoldAHeader() {
        // Header là 7 byte (NETWORK.md §102). 0 byte là ca thật: server đóng nửa chừng thì đọc ra
        // một khung rỗng, và cấp phát nó chỉ để PacketCodec ném tiếp ở tầng sau là đi vòng.
        for (int tooShort : new int[] {0, 1, 6}) {
            assertThatThrownBy(() -> GameConnection.checkedFrameLength(tooShort))
                    .as("độ dài %d", tooShort)
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void shouldAcceptEveryLengthTheServerItselfWouldSend() throws IOException {
        // Hai biên phải ĐI QUA, không thì bản vá lại thành một bug khác: khung nhỏ nhất hợp lệ là
        // header trơn không payload, lớn nhất là đúng trần server tự đặt.
        assertThat(GameConnection.checkedFrameLength(7)).isEqualTo(7);
        assertThat(GameConnection.checkedFrameLength(MAX)).isEqualTo(MAX);
        assertThatCode(() -> GameConnection.checkedFrameLength(64)).doesNotThrowAnyException();
    }
}
