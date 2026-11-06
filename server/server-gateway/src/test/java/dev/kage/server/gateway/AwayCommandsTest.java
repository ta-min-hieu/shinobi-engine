package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.SetAwayMessageCommand;
import dev.kage.protocol.SetAwayMessageOutcome;
import dev.kage.protocol.SetAwayMessageResponse;

/** ADR-178 — lời nhắn vắng: chuẩn hoá, giới hạn, nhịp chặn lặp, dọn khi thoát. */
final class AwayCommandsTest {

    private static final long ME = 1L;
    private static final long FRIEND = 2L;

    /** Đồng hồ chỉnh tay được. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-06T12:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void setNormalisesAndEmptyTurnsOff() {
        AwayCommands away = new AwayCommands(new MutableClock());
        SetAwayMessageResponse on = away.set(ME, command("  đi   ăn\ncơm  "));
        assertThat(on.getAway()).isTrue();
        assertThat(on.getMessage()).isEqualTo("đi ăn cơm");
        SetAwayMessageResponse off = away.set(ME, command("   "));
        assertThat(off.getAway()).isFalse();
        assertThat(away.replyFor(ME, FRIEND)).isEmpty();
    }

    @Test
    void tooLongAndBannedAreRefusedAndKeepThePreviousMessage() {
        AwayCommands away = new AwayCommands(new MutableClock());
        away.set(ME, command("bận"));
        SetAwayMessageResponse tooLong = away.set(ME, command("a".repeat(AwayCommands.MAX_LENGTH + 1)));
        assertThat(tooLong.getOutcome()).isEqualTo(SetAwayMessageOutcome.SET_AWAY_MESSAGE_OUTCOME_TOO_LONG);
        assertThat(tooLong.getMessage()).isEqualTo("bận");
        SetAwayMessageResponse banned = away.set(ME, command("đồ ngu"));
        assertThat(banned.getOutcome()).isEqualTo(SetAwayMessageOutcome.SET_AWAY_MESSAGE_OUTCOME_BANNED_WORD);
    }

    @Test
    void repliesOncePerSenderPerIntervalAndNeverToSelf() {
        MutableClock clock = new MutableClock();
        AwayCommands away = new AwayCommands(clock);
        away.set(ME, command("bận"));
        assertThat(away.replyFor(ME, FRIEND)).contains("bận");
        assertThat(away.replyFor(ME, FRIEND)).isEmpty();
        assertThat(away.replyFor(ME, ME)).isEmpty();
        clock.now = clock.now.plus(AwayCommands.REPLY_INTERVAL).plus(Duration.ofSeconds(1));
        assertThat(away.replyFor(ME, FRIEND)).contains("bận");
        away.clear(ME);
        assertThat(away.replyFor(ME, FRIEND)).isEmpty();
    }

    private static SetAwayMessageCommand command(String message) {
        return SetAwayMessageCommand.newBuilder().setMessage(message).build();
    }
}
