package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.Opcode;

/** ADR-214 — đọc cấu hình, và chỉ gói KHÁC {@code PING} mới tính là người chơi còn hoạt động. */
class IdleKickTest {

    @Test
    void shouldParseMinutes_defaultWhenMissing_andRejectGarbage() {
        assertThat(IdleKick.parse(null)).isEqualTo(Duration.ofMinutes(IdleKick.DEFAULT_MINUTES));
        assertThat(IdleKick.parse(" 5 ")).isEqualTo(Duration.ofMinutes(5));
        assertThat(IdleKick.parse("0")).isZero();
        assertThatThrownBy(() -> IdleKick.parse("abc")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> IdleKick.parse("-1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldIgnorePings_andResetOnAnyOtherPacket() {
        IdleKick kick = new IdleKick(Duration.ofMinutes(1));
        long later = System.nanoTime() + Duration.ofMinutes(2).toNanos();

        kick.touch(Opcode.OPCODE_PING_VALUE);
        assertThat(kick.isIdle(later)).isTrue();

        kick.touch(Opcode.OPCODE_MOVE_COMMAND_VALUE);
        assertThat(kick.isIdle(System.nanoTime())).isFalse();
    }

    @Test
    void shouldNeverBeIdle_whenTurnedOff() {
        IdleKick off = new IdleKick(Duration.ZERO);

        assertThat(off.isIdle(System.nanoTime() + Duration.ofDays(1).toNanos())).isFalse();
    }
}
