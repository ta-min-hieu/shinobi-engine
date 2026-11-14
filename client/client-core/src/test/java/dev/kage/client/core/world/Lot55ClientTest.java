package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.SendChatMessageOutcome;

/** Lô 55 phía client — nhớ kênh (ADR-371), câu báo cấp tối thiểu / người lạ (ADR-368/369). */
class Lot55ClientTest {

    @Test
    void restoresOnlyRotatingChannels() {
        ChatController controller = new ChatController();
        assertThat(controller.selectChannel(ChatChannel.CHAT_CHANNEL_TRADE)).isTrue();
        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_TRADE);
        assertThat(controller.selectChannel(ChatChannel.CHAT_CHANNEL_PARTY))
                .as("chưa vào nhóm")
                .isFalse();
        assertThat(controller.selectChannel(ChatChannel.CHAT_CHANNEL_PRIVATE)).isFalse();
        assertThat(controller.channel()).isEqualTo(ChatChannel.CHAT_CHANNEL_TRADE);
    }

    @Test
    void newOutcomesHaveMessages() {
        assertThat(ChatController.outcomeMessageKey(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_LEVEL_TOO_LOW))
                .contains("chat.error.level_too_low");
        assertThat(ChatController.outcomeMessageKey(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_STRANGER_LIMIT))
                .contains("chat.error.stranger_limit");
    }
}
