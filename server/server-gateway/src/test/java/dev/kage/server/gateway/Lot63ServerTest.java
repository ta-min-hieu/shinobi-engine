package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import dev.kage.character.api.NewPlayersFacade;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;

/** Lô 63 — giây online hôm nay của phiên đang mở (ADR-432), dòng bang khi đồng môn đột phá (ADR-433). */
class Lot63ServerTest {

    @Test
    void countsLiveSecondsOnlyFromStartOfDay() {
        Instant start = NewPlayersFacade.startOfDay(Instant.parse("2033-03-03T12:00:00Z"));
        Instant now = start.plusSeconds(3600);
        assertThat(Lot63Commands.liveSecondsToday(start.minusSeconds(7200), now))
                .isEqualTo(3600);
        assertThat(Lot63Commands.liveSecondsToday(now.minusSeconds(600), now)).isEqualTo(600);
        assertThat(Lot63Commands.liveSecondsToday(now.plusSeconds(5), now)).isZero();
    }

    @Test
    void breakthroughLineIsGuildChannelWithoutSender() throws Exception {
        ChatMessageEvent event = ChatMessageEvent.parseFrom(Lot63Commands.breakthroughLine("ĐạoHữu01", 10));
        assertThat(event.getChannel()).isEqualTo(ChatChannel.CHAT_CHANNEL_GUILD);
        assertThat(event.getSenderName()).isEmpty();
        assertThat(event.getBody()).contains("ĐạoHữu01").contains("10");
    }
}
