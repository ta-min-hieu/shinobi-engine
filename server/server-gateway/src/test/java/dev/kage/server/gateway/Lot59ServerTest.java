package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.friend.api.WhisperPrivacyFacade;
import dev.kage.protocol.ChatMessageEvent;

/** Lô 59 — tin bang gần nhất (ADR-401), luật nhắn riêng (ADR-402), dòng báo đã đọc (ADR-400). */
class Lot59ServerTest {

    @Test
    void recentKeepsNewestInOrder() {
        GuildChatBacklog backlog = new GuildChatBacklog();
        for (int index = 0; index < 5; index++) {
            backlog.record(7L, new GuildChatBacklog.Line("A", "tin " + index, Instant.EPOCH.plusSeconds(index)));
        }
        assertThat(backlog.recent(7L, 3))
                .extracting(GuildChatBacklog.Line::body)
                .containsExactly("tin 2", "tin 3", "tin 4");
        assertThat(backlog.recent(8L, 3)).isEmpty();
    }

    @Test
    void whisperRule() {
        assertThat(WhisperPrivacyFacade.allows(false, false, false)).isTrue();
        assertThat(WhisperPrivacyFacade.allows(true, false, false)).isFalse();
        assertThat(WhisperPrivacyFacade.allows(true, true, false)).isTrue();
        assertThat(WhisperPrivacyFacade.allows(true, false, true)).isTrue();
    }

    @Test
    void receiptLineNamesReaderAndSubject() throws InvalidProtocolBufferException {
        ChatMessageEvent event = ChatMessageEvent.parseFrom(Lot59Commands.receiptLine("BaoDo01", "Hẹn"));
        assertThat(event.getSenderName()).isEmpty();
        assertThat(event.getBody()).contains("BaoDo01").contains("Hẹn");
    }
}
