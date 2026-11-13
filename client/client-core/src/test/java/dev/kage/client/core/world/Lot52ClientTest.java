package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;

/** Lô 52 phía client — chống chữ hoa (ADR-348), lưu chat (ADR-347), xác nhận bán (ADR-349). */
class Lot52ClientTest {

    @Test
    void detectsShouting() {
        assertThat(CapsCalmCoordinator.isShouting("BÁN KIẾM GIÁ RẺ")).isTrue();
        assertThat(CapsCalmCoordinator.isShouting("OK")).isFalse();
        assertThat(CapsCalmCoordinator.isShouting("Bán Kiếm")).isFalse();
        assertThat(CapsCalmCoordinator.isShouting("12345 !!!")).isFalse();
    }

    @Test
    void formatsChatLogLine() {
        ChatMessageEvent event = ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                .setSenderName("BaoDo01")
                .setBody("chào")
                .build();
        assertThat(ChatLogExportCoordinator.format(LocalDateTime.of(2026, 11, 13, 9, 5, 7), event))
                .isEqualTo("[09:05:07] (world) BaoDo01: chào");
        assertThat(ChatLogExportCoordinator.format(
                        LocalDateTime.of(2026, 11, 13, 9, 5, 7),
                        event.toBuilder().clearSenderName().build()))
                .contains(") *: chào");
    }

    @Test
    void enhancedItemsNeedASecondPress() {
        AtomicLong now = new AtomicLong(1_000L);
        List<Long> asked = new ArrayList<>();
        SellConfirmGuard guard = new SellConfirmGuard(now::get, item -> asked.add(item.itemId()));
        InventoryController.Item plain = new InventoryController.Item(1L, 10L, 1, 0, 0);
        InventoryController.Item enhanced = new InventoryController.Item(2L, 10L, 1, 5, 1);
        assertThat(guard.confirm(plain)).isTrue();
        assertThat(guard.confirm(enhanced)).isFalse();
        now.addAndGet(1_000L);
        assertThat(guard.confirm(enhanced)).isTrue();
        assertThat(guard.confirm(enhanced)).isFalse();
        now.addAndGet(SellConfirmGuard.WINDOW_MILLIS + 1);
        assertThat(guard.confirm(enhanced)).isFalse();
        assertThat(asked).containsExactly(2L, 2L, 2L);
    }
}
