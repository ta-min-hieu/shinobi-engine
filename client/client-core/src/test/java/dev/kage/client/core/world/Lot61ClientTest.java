package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;

/** Lô 61 phía client — gộp tin trùng (ADR-420), cú pháp câu nói nhanh (ADR-422). */
class Lot61ClientTest {

    @Test
    void collapsesConsecutiveRepeats() {
        ChatController controller = new ChatController();
        controller.setCollapseRepeats(true);
        ChatMessageEvent spam = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                .setSenderName("A")
                .setBody("mua đồ")
                .build();
        controller.onMessage(spam);
        controller.onMessage(spam);
        controller.onMessage(spam);
        assertThat(controller.lines()).hasSize(1);
        assertThat(controller.lines().getFirst().body()).isEqualTo("mua đồ (x3)");
        controller.onMessage(spam.toBuilder().setSenderName("B").build());
        assertThat(controller.lines()).hasSize(2);
    }

    @Test
    void keepsRepeatsWhenOff() {
        ChatController controller = new ChatController();
        ChatMessageEvent spam = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                .setSenderName("A")
                .setBody("x")
                .build();
        controller.onMessage(spam);
        controller.onMessage(spam);
        assertThat(controller.lines()).hasSize(2);
    }

    @Test
    void parsesPhraseSlot() {
        assertThat(ChatTidyCoordinator.phraseSlot("/cn 3")).contains(3);
        assertThat(ChatTidyCoordinator.phraseSlot("/CN 5")).contains(5);
        assertThat(ChatTidyCoordinator.phraseSlot("/cn 6")).isEmpty();
        assertThat(ChatTidyCoordinator.phraseSlot("/cn")).isEmpty();
        assertThat(ChatTidyCoordinator.phraseSlot("xin chào")).isEmpty();
    }
}
