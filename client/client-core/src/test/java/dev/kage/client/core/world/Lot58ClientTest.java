package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.hud.ChatPanel;
import dev.kage.protocol.ChatChannel;

/** Lô 58 phía client — cuộn chat (ADR-395), kênh lọc bạn bè (ADR-397), ngày vào bang (ADR-393). */
class Lot58ClientTest {

    @Test
    void clampsScrollInsideLog() {
        assertThat(ChatPanel.clampScroll(5, 40, 6)).isEqualTo(5);
        assertThat(ChatPanel.clampScroll(100, 40, 6)).isEqualTo(34);
        assertThat(ChatPanel.clampScroll(-3, 40, 6)).isZero();
        assertThat(ChatPanel.clampScroll(4, 3, 6)).as("nhật ký chưa đầy khung").isZero();
    }

    @Test
    void friendFilterOnlyTouchesPublicChannels() {
        assertThat(ChatViewCoordinator.isPublic(ChatChannel.CHAT_CHANNEL_WORLD)).isTrue();
        assertThat(ChatViewCoordinator.isPublic(ChatChannel.CHAT_CHANNEL_TRADE)).isTrue();
        assertThat(ChatViewCoordinator.isPublic(ChatChannel.CHAT_CHANNEL_GUILD)).isFalse();
        assertThat(ChatViewCoordinator.isPublic(ChatChannel.CHAT_CHANNEL_PRIVATE))
                .isFalse();
    }

    @Test
    void formatsJoinDate() {
        assertThat(LifetimeRankingCoordinator.dateOf(1_956_528_000L, ZoneOffset.UTC))
                .isEqualTo("01/01/2032");
    }
}
