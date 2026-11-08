package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ChatMessageEvent;

/** ADR-256 — dòng nhắc làm tròn phút lên, tối thiểu 1. */
class GuildEventReminderTest {

    @Test
    void roundsMinutesUp() throws InvalidProtocolBufferException {
        assertThat(body(Duration.ofSeconds(9 * 60 + 10)))
                .contains("sau 10 phút")
                .contains("in 10 minutes");
        assertThat(body(Duration.ZERO)).contains("sau 1 phút");
    }

    private static String body(Duration left) throws InvalidProtocolBufferException {
        return ChatMessageEvent.parseFrom(GuildEventReminder.notice("Đi bí cảnh", left))
                .getBody();
    }
}
