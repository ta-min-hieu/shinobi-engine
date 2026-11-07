package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.ItemCatalogEntry;

/** ADR-199 — so sánh với món đang mặc theo chỉ số hiệu dụng (hệ số server gửi). */
final class InventoryItemTextComparisonTest {

    private static final long WORN_ID = 10L;
    private static final long BAG_ID = 11L;

    private static final Map<Long, ItemCatalogEntry> CATALOG = Map.of(
            1L,
                    ItemCatalogEntry.newBuilder()
                            .setId(1L)
                            .setEquipSlot(EquipmentSlot.EQUIPMENT_SLOT_WEAPON)
                            .setBonusAtk(10)
                            .build(),
            2L,
                    ItemCatalogEntry.newBuilder()
                            .setId(2L)
                            .setEquipSlot(EquipmentSlot.EQUIPMENT_SLOT_WEAPON)
                            .setBonusAtk(12)
                            .setBonusDef(2)
                            .build());

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void effectiveMatchesTheServerFormula() {
        assertThat(InventoryItemText.effective(25, 13, 10))
                .as("57,5 làm tròn lên như server")
                .isEqualTo(58);
        assertThat(InventoryItemText.effective(10, 0, 10)).isEqualTo(10);
        assertThat(InventoryItemText.effective(0, 5, 10)).isZero();
    }

    @Test
    void showsSignedDifferencesAgainstTheWornItem() {
        InventoryController.Item worn = new InventoryController.Item(WORN_ID, 1L, 1, 3, 0); // công 10 × 1,3 = 13
        InventoryController.Item bag = new InventoryController.Item(BAG_ID, 2L, 1, 0, 1); // công 12, thủ 2
        InventoryItemText text = new InventoryItemText(CATALOG::get, id -> "#" + id);
        text.useComparison(new InventoryItemText.Comparison(item -> Optional.of(worn), () -> 10));

        String line = text.describe(bag);

        assertThat(line).contains("-1").contains("+2").doesNotContain("compare.");
        assertThat(text.describe(worn)).as("chính món đang mặc thì không so").doesNotContain("-1");
    }

    @Test
    void staysSilentWithoutTheServerStep() {
        InventoryController.Item bag = new InventoryController.Item(BAG_ID, 2L, 1, 0, 1);
        InventoryItemText text = new InventoryItemText(CATALOG::get, id -> "#" + id);
        text.useComparison(new InventoryItemText.Comparison(item -> Optional.empty(), () -> 0));

        assertThat(text.describe(bag)).doesNotContain("\n" + "Ô").doesNotContain("compare.");
        assertThat(text.describe(bag).lines().count()).isEqualTo(2L); // tên + chỉ số gốc
    }
}
