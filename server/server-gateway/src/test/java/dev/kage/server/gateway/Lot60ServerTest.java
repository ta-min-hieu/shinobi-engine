package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;

/** Lô 60 — cấp tối thiểu gửi thư (ADR-407), phát lại chat thế giới (ADR-408), tuổi nhân vật (ADR-410). */
class Lot60ServerTest {

    @Test
    void parsesMailMinLevelWithOwnFallback() {
        assertThat(Lot55Commands.parseMinLevel(null, 5, "X")).isEqualTo(5);
        assertThat(Lot55Commands.parseMinLevel(" 0 ", 5, "X")).isZero();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Lot55Commands.parseMinLevel("năm", 5, "KAGE_X"))
                .hasMessageContaining("KAGE_X");
    }

    @Test
    void worldReplayGoesToWorldChannelWithTime() throws InvalidProtocolBufferException {
        ChatMessageEvent event = ChatMessageEvent.parseFrom(Lot60Commands.worldReplay(
                new GuildChatBacklog.Line("BaoDo01", "chào", Instant.parse("2032-01-01T13:05:00Z"))));
        assertThat(event.getChannel()).isEqualTo(ChatChannel.CHAT_CHANNEL_WORLD);
        assertThat(event.getSenderName()).isEqualTo("BaoDo01");
        assertThat(event.getBody()).isEqualTo("[13:05 UTC] chào");
    }

    @Test
    void ageInWholeDays() {
        Instant created = Instant.parse("2032-01-01T00:00:00Z");
        assertThat(Lot60Commands.ageDays(created, created.plus(Duration.ofHours(47))))
                .isEqualTo(1);
        assertThat(Lot60Commands.ageDays(created, created.minusSeconds(5))).isZero();
    }
}
