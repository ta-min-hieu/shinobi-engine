package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;

/** Lô 48 — lời chào bang (ADR-313), báo thư chưa đọc (ADR-314). */
class Lot48ServerTest {

    @Test
    void validatesWelcomeMessage() {
        assertThat(Lot48Commands.validWelcome("Chào mừng đạo hữu!")).isTrue();
        assertThat(Lot48Commands.validWelcome("")).isTrue();
        assertThat(Lot48Commands.validWelcome("a".repeat(201))).isFalse();
        assertThat(Lot48Commands.validWelcome("xin\u0000chào")).isFalse();
    }

    @Test
    void noticesAreBilingual() throws InvalidProtocolBufferException {
        assertThat(ChatMessageEvent.parseFrom(Lot48Commands.unreadNotice(3)).getBody())
                .contains("3 thư chưa đọc")
                .contains("3 unread mail");
        ChatMessageEvent welcome = ChatMessageEvent.parseFrom(GuildWelcomeHooks.line("Thanh Vân", "Chào bạn"));
        assertThat(welcome.getChannel()).isEqualTo(ChatChannel.CHAT_CHANNEL_GUILD);
        assertThat(welcome.getBody()).isEqualTo("[Thanh Vân] Chào bạn");
    }
}
