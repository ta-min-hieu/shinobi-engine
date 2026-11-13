package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;

/** Lô 47 — tin bang lúc vắng (ADR-304), báo trước sự kiện (ADR-306). */
class Lot47ServerTest {

    private static final Instant T0 = Instant.parse("2026-11-13T10:00:00Z");

    @Test
    void backlogKeepsNewestFiftyAndFiltersByTime() {
        GuildChatBacklog backlog = new GuildChatBacklog();
        for (int i = 0; i < GuildChatBacklog.CAPACITY + 5; i++) {
            backlog.record(1L, new GuildChatBacklog.Line("A", "m" + i, T0.plusSeconds(i)));
        }
        assertThat(backlog.since(1L, Instant.EPOCH)).hasSize(GuildChatBacklog.CAPACITY);
        assertThat(backlog.since(1L, Instant.EPOCH).getFirst().body()).isEqualTo("m5");
        assertThat(backlog.since(1L, T0.plusSeconds(52)))
                .extracting(GuildChatBacklog.Line::body)
                .containsExactly("m53", "m54");
        assertThat(backlog.since(2L, Instant.EPOCH)).isEmpty();
    }

    @Test
    void replayCarriesGuildChannelAndTime() throws InvalidProtocolBufferException {
        ChatMessageEvent event =
                ChatMessageEvent.parseFrom(Lot47Commands.replay(new GuildChatBacklog.Line("BaoDo01", "chào", T0)));
        assertThat(event.getChannel()).isEqualTo(ChatChannel.CHAT_CHANNEL_GUILD);
        assertThat(event.getSenderName()).isEqualTo("BaoDo01");
        assertThat(event.getBody()).isEqualTo("[10:00 UTC] chào");
    }

    @Test
    void eventReminderWindowAndMinutes() throws InvalidProtocolBufferException {
        assertThat(EventReminder.due(T0.plus(Duration.ofMinutes(5)), T0)).isTrue();
        assertThat(EventReminder.due(T0.plus(Duration.ofMinutes(6)), T0)).isFalse();
        assertThat(EventReminder.due(T0, T0)).isFalse();
        assertThat(EventReminder.minutesLeft(T0.plusSeconds(241), T0)).isEqualTo(5);
        assertThat(EventReminder.minutesLeft(T0.plusSeconds(10), T0)).isEqualTo(1);
        assertThat(ChatMessageEvent.parseFrom(EventReminder.notice(EventReminder.Kind.TOURNAMENT, 5))
                        .getBody())
                .contains("Luận võ đại hội bắt đầu sau 5 phút")
                .contains("starts in 5 minutes");
    }
}
