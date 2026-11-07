package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.LinkedItemView;

/** ADR-188 — nhãn món khoe dựng từ dữ liệu server. */
final class ItemLinkCoordinatorTest {

    @Test
    void labelShowsEnhanceGemAndStack() {
        LinkedItemView sword = LinkedItemView.newBuilder()
                .setItemTemplateId(10)
                .setEnhanceLevel(3)
                .setSocketGemTemplateId(48)
                .setQuantity(1)
                .build();
        assertThat(ItemLinkCoordinator.label(sword, id -> id == 10 ? "Trường kiếm" : "Hồng Ngọc"))
                .isEqualTo("[Trường kiếm +3 | Hồng Ngọc]");
        LinkedItemView pills = LinkedItemView.newBuilder()
                .setItemTemplateId(20)
                .setQuantity(12)
                .build();
        assertThat(ItemLinkCoordinator.label(pills, id -> "Đan")).isEqualTo("[Đan x12]");
    }
}
