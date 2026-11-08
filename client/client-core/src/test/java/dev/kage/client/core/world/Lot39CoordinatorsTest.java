package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.ItemRewardView;

/** Lô 39 — phút còn lại của buff bang (ADR-239), danh sách vật phẩm xem trước thưởng (ADR-242). */
class Lot39CoordinatorsTest {

    @Test
    void shouldRoundMinutesUp() {
        assertThat(GuildBuffCoordinator.minutesLeft(0)).isZero();
        assertThat(GuildBuffCoordinator.minutesLeft(1)).isEqualTo(1);
        assertThat(GuildBuffCoordinator.minutesLeft(3600)).isEqualTo(60);
        assertThat(GuildBuffCoordinator.minutesLeft(3601)).isEqualTo(61);
    }

    @Test
    void shouldListRewardItems() {
        assertThat(QuestRewardPreviewCoordinator.itemList(List.of())).isEmpty();
        assertThat(QuestRewardPreviewCoordinator.itemList(List.of(
                        ItemRewardView.newBuilder()
                                .setItemTemplateCode("kunai")
                                .setQuantity(5)
                                .build(),
                        ItemRewardView.newBuilder()
                                .setItemTemplateCode("wooden_sword")
                                .setQuantity(1)
                                .build())))
                .isEqualTo("kunai x5, wooden_sword x1");
    }
}
