package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;

/** ADR-222 — {@code /gd} gửi lên kênh giao dịch; tắt hiển thị kênh thì tin tới không vào khung chat. */
class TradeChannelTest {

    @Test
    void shouldComposeTradeMessagesWithThePrefix() {
        ChatController controller = new ChatController();

        assertThat(controller.compose("/gd bán 10 đan"))
                .isEqualTo(new ChatController.ComposeResult.Send(ChatChannel.CHAT_CHANNEL_TRADE, "", "bán 10 đan"));
        assertThat(controller.compose("/TRADE buying swords"))
                .isEqualTo(new ChatController.ComposeResult.Send(ChatChannel.CHAT_CHANNEL_TRADE, "", "buying swords"));
    }

    @Test
    void shouldDropHiddenChannels() {
        ChatController controller = new ChatController();
        ChatMessageEvent trade = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_TRADE)
                .setSenderName("A")
                .setBody("bán")
                .build();

        controller.setChannelVisible(ChatChannel.CHAT_CHANNEL_TRADE, false);
        controller.onMessage(trade);
        assertThat(controller.lines()).isEmpty();
        controller.setChannelVisible(ChatChannel.CHAT_CHANNEL_TRADE, true);
        controller.onMessage(trade);
        assertThat(controller.lines()).hasSize(1);
        assertThat(GuildInviteCoordinator.keyOf("INVITE_TO_GUILD_OUTCOME_OK", "INVITE_TO_GUILD_OUTCOME_"))
                .isEqualTo("ok");
    }
}
