package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.EquipItemCommand;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.ItemCatalogEntry;
import dev.kage.protocol.ItemCatalogResponse;

class ItemCatalogTest {

    private static final long SWORD_ID = 11L;
    private static final long NEW_SLOT_ITEM_ID = 12L;

    /** Số ô trang bị mà {@code inventory.proto} của client này chưa biết — server mới hơn thêm vào. */
    private static final int SLOT_FROM_A_NEWER_SERVER = 99;

    private static ItemCatalog catalogWithAnItemInAnUnknownSlot() {
        ItemCatalog catalog = new ItemCatalog();
        catalog.adopt(ItemCatalogResponse.newBuilder()
                .setVersion("v2")
                .addEntries(ItemCatalogEntry.newBuilder()
                        .setId(SWORD_ID)
                        .setCode("steel_sword")
                        .setEquipSlot(EquipmentSlot.EQUIPMENT_SLOT_WEAPON))
                .addEntries(ItemCatalogEntry.newBuilder()
                        .setId(NEW_SLOT_ITEM_ID)
                        .setCode("jade_belt")
                        .setEquipSlotValue(SLOT_FROM_A_NEWER_SERVER))
                .build());
        return catalog;
    }

    /**
     * Ô trang bị lạ (protobuf {@code UNRECOGNIZED}) phải được coi là "không mặc được" ngay tại
     * catalog — nguồn duy nhất mà {@code InventoryCoordinator} hỏi trước khi bày nút Mặc và trước
     * khi dựng {@code EquipItemCommand}.
     *
     * <p>Trước bản vá, {@code UNRECOGNIZED} lọt qua phép so {@code == UNSPECIFIED}: nút Mặc hiện ra,
     * và bấm nó là {@code EquipItemCommand.Builder.setSlot(UNRECOGNIZED)} ném
     * {@code IllegalArgumentException} trên luồng render — sập cả ứng dụng (xem ô test dưới).
     */
    @Test
    void shouldTreatAnUnknownEquipSlotAsNotEquippable() {
        ItemCatalog catalog = catalogWithAnItemInAnUnknownSlot();

        assertThat(catalog.equipSlot(NEW_SLOT_ITEM_ID)).isEqualTo(EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED);
        assertThat(catalog.equipSlot(SWORD_ID)).isEqualTo(EquipmentSlot.EQUIPMENT_SLOT_WEAPON);
    }

    /** Mọi ô catalog trả ra đều phải dựng được lệnh mặc mà không ném — đúng thứ nút Mặc sẽ làm. */
    @Test
    void everySlotTheCatalogReturnsMustBeSendable() {
        ItemCatalog catalog = catalogWithAnItemInAnUnknownSlot();

        for (long itemId : new long[] {SWORD_ID, NEW_SLOT_ITEM_ID}) {
            EquipmentSlot slot = catalog.equipSlot(itemId);
            assertThat(EquipItemCommand.newBuilder().setItemId(itemId).setSlot(slot).build().getSlot())
                    .isEqualTo(slot);
        }
    }
}
