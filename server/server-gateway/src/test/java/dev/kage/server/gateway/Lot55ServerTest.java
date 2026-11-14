package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ChatMessageEvent;

/** Lô 55 — chống spam tin riêng người lạ (ADR-369), dòng báo đổi lời nhắn (ADR-370). */
class Lot55ServerTest {

    @Test
    void limitsDistinctStrangersPerWindow() {
        StrangerWhisperLimiter limiter = new StrangerWhisperLimiter();
        for (long stranger = 1; stranger <= StrangerWhisperLimiter.MAX_STRANGERS; stranger++) {
            assertThat(limiter.allow(99L, stranger, 0L)).isTrue();
        }
        assertThat(limiter.allow(99L, 100L, 1_000L)).as("người lạ thứ sáu").isFalse();
        assertThat(limiter.allow(99L, 3L, 1_000L)).as("nhắn tiếp người đã nhắn").isTrue();
        assertThat(limiter.allow(42L, 100L, 1_000L)).as("người gửi khác").isTrue();
        assertThat(limiter.allow(99L, 100L, StrangerWhisperLimiter.WINDOW_MILLIS + 1_000L))
                .as("hết cửa sổ")
                .isTrue();
    }

    @Test
    void validatesOfficerBody() {
        assertThat(Lot55Commands.validOfficerBody("họp bang tối nay")).isTrue();
        assertThat(Lot55Commands.validOfficerBody("")).isFalse();
        assertThat(Lot55Commands.validOfficerBody("x".repeat(201))).isFalse();
    }

    @Test
    void parsesMinLevel() {
        assertThat(Lot55Commands.parseMinLevel(null)).isEqualTo(Lot55Commands.DEFAULT_WORLD_CHAT_MIN_LEVEL);
        assertThat(Lot55Commands.parseMinLevel(" 0 ")).isZero();
        assertThat(Lot55Commands.parseMinLevel("10")).isEqualTo(10);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Lot55Commands.parseMinLevel("abc"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void statusLineNamesFriend() throws InvalidProtocolBufferException {
        assertThat(ChatMessageEvent.parseFrom(Lot55Commands.statusLine("BaoDo01", "Bế quan"))
                        .getBody())
                .startsWith("BaoDo01 đổi lời nhắn: \"Bế quan\"");
    }
}
