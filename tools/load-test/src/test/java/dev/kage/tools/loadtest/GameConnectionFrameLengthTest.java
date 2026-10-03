package dev.kage.tools.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Độ dài khung trên dây không hợp lệ phải thành {@link IOException} (đi đường đếm lỗi), không phải
 * {@code NegativeArraySizeException}/{@code IllegalArgumentException} giết luồng client im lặng.
 */
final class GameConnectionFrameLengthTest {

    @Test
    void shouldRejectNegativeShortAndOversizedLengths() {
        assertThatThrownBy(() -> GameConnection.checkedFrameLength(-1)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> GameConnection.checkedFrameLength(6)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> GameConnection.checkedFrameLength(1024 * 1024 + 1))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldAcceptHeaderOnlyAndMaximumFrames() throws IOException {
        assertThat(GameConnection.checkedFrameLength(7)).isEqualTo(7);
        assertThat(GameConnection.checkedFrameLength(1024 * 1024)).isEqualTo(1024 * 1024);
    }
}
