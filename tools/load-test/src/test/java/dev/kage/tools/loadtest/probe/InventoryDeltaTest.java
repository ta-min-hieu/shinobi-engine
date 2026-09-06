package dev.kage.tools.loadtest.probe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.InventoryItemView;

/** Chênh lệch túi đồ giữa hai lần chụp — thuần logic, không cần server. */
final class InventoryDeltaTest {

    @Test
    void shouldReportNothing_whenInventoryIsUnchanged() {
        List<InventoryItemView> same = List.of(item(11L, 100L, 3));

        assertThat(InventoryDelta.between(same, same)).isEmpty();
        assertThat(InventoryDelta.renderTable(List.of())).contains("túi đồ không đổi");
    }

    @Test
    void shouldReportRewardArrivingInBrandNewSlot() {
        List<InventoryDelta.Line> lines = InventoryDelta.between(List.of(), List.of(item(11L, 100L, 2)));

        assertThat(lines).containsExactly(new InventoryDelta.Line(100L, 0, 2));
        assertThat(lines.getFirst().change()).isEqualTo(2);
    }

    /**
     * Cùng một loại vật phẩm nằm ở NHIỀU ô (xếp chồng tới {@code max_stack}) phải ra MỘT dòng chênh
     * lệch — server có quyền cộng vào ô cũ hay mở ô mới, hai cách đó không được cho ra hai kết luận.
     */
    @Test
    void shouldSumEverySlotOfTheSameTemplate() {
        List<InventoryDelta.Line> lines =
                InventoryDelta.between(List.of(item(11L, 100L, 20)), List.of(item(11L, 100L, 20), item(12L, 100L, 5)));

        assertThat(lines).containsExactly(new InventoryDelta.Line(100L, 20, 25));
    }

    @Test
    void shouldReportItemsConsumed() {
        List<InventoryDelta.Line> lines =
                InventoryDelta.between(List.of(item(11L, 100L, 3)), List.of(item(11L, 100L, 1)));

        assertThat(lines.getFirst().change()).isEqualTo(-2);
        assertThat(InventoryDelta.renderTable(lines))
                .contains("itemTemplateId=100")
                .contains("(-2)");
    }

    @Test
    void shouldSortLinesByTemplateId_soTwoRunsCompare() {
        List<InventoryDelta.Line> lines =
                InventoryDelta.between(List.of(), List.of(item(11L, 300L, 1), item(12L, 100L, 1), item(13L, 200L, 1)));

        assertThat(lines.stream().map(InventoryDelta.Line::itemTemplateId)).containsExactly(100L, 200L, 300L);
    }

    private static InventoryItemView item(long itemId, long templateId, int quantity) {
        return InventoryItemView.newBuilder()
                .setItemId(itemId)
                .setItemTemplateId(templateId)
                .setQuantity(quantity)
                .build();
    }
}
