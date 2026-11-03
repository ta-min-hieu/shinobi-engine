package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.EnhanceOutcome;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.EquippedSlotView;
import dev.kage.protocol.InventoryItemView;
import dev.kage.protocol.ListInventoryResponse;

/**
 * {@link InventoryController} thuần Java, không libGDX — chạy được thẳng trong JUnit như
 * {@code MailControllerTest}.
 */
class InventoryControllerTest {

    private final InventoryController controller = new InventoryController();

    @Test
    @DisplayName("Túi bắt đầu rỗng, sức chứa 0, chưa chọn gì, chưa có gì mặc")
    void startsEmpty() {
        assertThat(controller.items()).isEmpty();
        assertThat(controller.capacity()).isZero();
        assertThat(controller.selected()).isEmpty();
        assertThat(controller.equippedItemId(EquipmentSlot.EQUIPMENT_SLOT_WEAPON))
                .isZero();
    }

    @Test
    @DisplayName("ListInventoryResponse là nguồn DUY NHẤT của túi đồ, giữ đủ 5 trường InventoryItemView")
    void adoptsServerInventory() {
        // Dựng thẳng bằng builder (không qua helper item()) vì đây là test DUY NHẤT cần
        // quantity/enhance_level khác giá trị mặc định — xem javadoc item() về lý do.
        InventoryItemView view = InventoryItemView.newBuilder()
                .setItemId(7L)
                .setItemTemplateId(1L)
                .setQuantity(3)
                .setEnhanceLevel(0)
                .setSlotIndex(2)
                .build();

        controller.onInventory(list(10, view));

        assertThat(controller.items()).containsExactly(new InventoryController.Item(7L, 1L, 3, 0, 2));
        assertThat(controller.capacity()).isEqualTo(10);
    }

    @Test
    @DisplayName("ADR-159 — cờ locked lấy từ ảnh chụp túi mới nhất, và ảnh chụp sau THAY hẳn ảnh trước")
    void tracksLockedItemsFromTheLatestSnapshot() {
        InventoryItemView locked = item(5L, 1L, 0).toBuilder().setLocked(true).build();

        controller.onInventory(list(10, locked, item(6L, 1L, 1)));

        assertThat(controller.isLocked(5L)).isTrue();
        assertThat(controller.isLocked(6L)).isFalse();

        controller.onInventory(list(10, item(5L, 1L, 0)));

        assertThat(controller.isLocked(5L)).isFalse();
    }

    @Test
    @DisplayName("Vật phẩm được sắp theo slot_index — không phải thứ tự server gửi")
    void sortsBySlotIndex() {
        controller.onInventory(list(10, item(1L, 1L, 3), item(2L, 1L, 0), item(3L, 1L, 1)));

        assertThat(controller.items())
                .extracting(InventoryController.Item::itemId)
                .containsExactly(2L, 3L, 1L);
    }

    @Test
    @DisplayName("Danh sách THAY hoàn toàn, không trộn với bản cũ")
    void replacesInventoryInsteadOfMerging() {
        controller.onInventory(list(10, item(1L, 1L, 0), item(2L, 1L, 1)));
        controller.onInventory(list(10, item(2L, 1L, 0)));

        assertThat(controller.items())
                .extracting(InventoryController.Item::itemId)
                .containsExactly(2L);
    }

    @Test
    @DisplayName("Item đang mặc VẪN nằm trong items — equipped chỉ mang slot -> item_id, tra tên qua byId")
    void keepsEquippedItemInItemsList() {
        controller.onInventory(ListInventoryResponse.newBuilder()
                .setCapacity(10)
                .addItems(item(5L, 2L, 0))
                .addEquipped(equipped(EquipmentSlot.EQUIPMENT_SLOT_WEAPON, 5L))
                .build());

        assertThat(controller.equippedItemId(EquipmentSlot.EQUIPMENT_SLOT_WEAPON))
                .isEqualTo(5L);
        assertThat(controller.byId(5L))
                .map(InventoryController.Item::itemTemplateId)
                .contains(2L);
    }

    @Test
    @DisplayName("Slot chưa mặc gì trả về 0 — 0 không đụng hàng vì inventory_item.id bắt đầu từ 1")
    void emptySlotReturnsZero() {
        controller.onInventory(list(10, item(1L, 1L, 0)));

        assertThat(controller.equippedItemId(EquipmentSlot.EQUIPMENT_SLOT_ARMOR))
                .isZero();
    }

    @Test
    @DisplayName("Chọn item: id có thật thì chọn, id lạ thì KHÔNG đổi gì")
    void selectsOnlyExistingItem() {
        controller.onInventory(list(10, item(5L, 1L, 0)));

        controller.select(999L);
        assertThat(controller.selected()).isEmpty();

        controller.select(5L);
        assertThat(controller.selected()).map(InventoryController.Item::itemId).contains(5L);
    }

    @Test
    @DisplayName("Item đang chọn biến mất khỏi danh sách mới thì BỎ chọn, không treo chi tiết cũ")
    void dropsSelectionWhenItemDisappears() {
        controller.onInventory(list(10, item(5L, 1L, 0)));
        controller.select(5L);

        controller.onInventory(list(10));

        assertThat(controller.selected()).isEmpty();
    }

    @Test
    @DisplayName("byId(0) luôn rỗng — 0 là sentinel 'không có item', không phải một id thật")
    void byIdZeroIsAlwaysEmpty() {
        controller.onInventory(list(10, item(1L, 1L, 0)));

        assertThat(controller.byId(0L)).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // Khoá i18n của error_key — inventory.proto KHÔNG có enum outcome cho Equip/Unequip (khác
    // ClaimMailOutcome), nên đây KHÔNG phải quét enum mà đối chiếu đúng 3 chuỗi THẬT server phát ra
    // (GatewayChannelHandler#toErrorKey/#handleUnequipItemCommandBlocking) — xem javadoc lớp.
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Ba error_key THẬT server phát cho Equip/Unequip đều có khoá i18n")
    void mapsEveryKnownErrorKey() {
        for (String errorKey : List.of(
                "error.item_not_found",
                "error.wrong_equipment_slot",
                "error.slot_not_equipped",
                "error.item_in_trade",
                "error.item_not_enhanceable",
                "error.item_on_cooldown",
                "error.inventory_full")) {
            assertThat(InventoryController.errorMessageKey(errorKey))
                    .as("error_key '%s' chưa có khoá i18n", errorKey)
                    .contains(errorKey);
        }
    }

    @Test
    @DisplayName("error_key lạ (server MỚI HƠN client) trả rỗng, không đoán bừa")
    void unknownErrorKeyIsUnmapped() {
        assertThat(InventoryController.errorMessageKey("error.some_future_key")).isEmpty();
    }

    @Test
    @DisplayName("clear() dọn sạch: rời màn hình rồi vào lại không thấy túi của phiên trước")
    void clearsEverything() {
        controller.onInventory(ListInventoryResponse.newBuilder()
                .setCapacity(10)
                .addItems(item(5L, 1L, 0))
                .addEquipped(equipped(EquipmentSlot.EQUIPMENT_SLOT_WEAPON, 5L))
                .build());
        controller.select(5L);

        controller.clear();

        assertThat(controller.items()).isEmpty();
        assertThat(controller.capacity()).isZero();
        assertThat(controller.selected()).isEmpty();
        assertThat(controller.equippedItemId(EquipmentSlot.EQUIPMENT_SLOT_WEAPON))
                .isZero();
    }

    // ---------------------------------------------------------------------------------------------
    // Cường hoá (opcode 505/506) — trạng thái Bảo Vệ + khoá idempotency + bảng tra outcome.
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Bảo Vệ mặc định TẮT — bùa là vật phẩm tiêu hao, không tự tiêu hộ người chơi")
    void protectionDefaultsOff() {
        assertThat(controller.useProtection()).isFalse();
    }

    @Test
    @DisplayName("Gạt công tắc Bảo Vệ đổi trạng thái và trả về trạng thái MỚI")
    void togglesProtection() {
        assertThat(controller.toggleProtection()).isTrue();
        assertThat(controller.useProtection()).isTrue();
        assertThat(controller.toggleProtection()).isFalse();
        assertThat(controller.useProtection()).isFalse();
    }

    @Test
    @DisplayName("Bấm Cường hoá lại trên CÙNG item khi lệnh chưa có kết luận giữ NGUYÊN khoá — đó là thử lại")
    void reusesEnhanceKeyWhilePending() {
        String first = controller.beginEnhance(5L);

        assertThat(controller.beginEnhance(5L)).isEqualTo(first);
        assertThat(controller.pendingEnhanceItemId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Gạt Bảo Vệ giữa chừng KHÔNG cấp khoá mới — vẫn là cùng một lần cường hoá đang treo")
    void protectionToggleDoesNotChangePendingKey() {
        String first = controller.beginEnhance(5L);
        controller.toggleProtection();

        assertThat(controller.beginEnhance(5L)).isEqualTo(first);
    }

    @Test
    @DisplayName("Đổi sang item khác là ý định MỚI — khoá mới")
    void newKeyForAnotherItem() {
        String first = controller.beginEnhance(5L);

        assertThat(controller.beginEnhance(6L)).isNotEqualTo(first);
    }

    @Test
    @DisplayName("ErrorResponse chỉ thuộc lệnh Cường hoá khi khớp sequenceId của gói ĐANG TREO")
    void claimsOnlyTheErrorOfThePendingEnhance() {
        controller.beginEnhance(5L);
        controller.onEnhanceSent(41);

        assertThat(controller.isPendingEnhance(41)).isTrue();
        assertThat(controller.isPendingEnhance(40)).as("lỗi của luồng khác").isFalse();
        controller.onEnhanceSettled();
        assertThat(controller.isPendingEnhance(41))
                .as("đã có kết luận thì không nhận nữa")
                .isFalse();
    }

    @Test
    @DisplayName("Lệnh đã có kết luận thì lần bấm sau là canh bạc MỚI — khoá mới, kể cả cùng item")
    void newKeyAfterSettle() {
        String first = controller.beginEnhance(5L);
        controller.onEnhanceSettled();

        assertThat(controller.pendingEnhanceItemId()).isZero();
        assertThat(controller.beginEnhance(5L)).isNotEqualTo(first);
    }

    @Test
    @DisplayName("Cả 8 outcome THẬT của EnhanceOutcome đều có câu để nói ra")
    void mapsEveryRealEnhanceOutcome() {
        for (EnhanceOutcome outcome : EnhanceOutcome.values()) {
            if (outcome == EnhanceOutcome.ENHANCE_OUTCOME_UNSPECIFIED || outcome == EnhanceOutcome.UNRECOGNIZED) {
                continue;
            }
            assertThat(InventoryController.enhanceMessageKey(outcome))
                    .as("%s chưa có khoá i18n", outcome)
                    .isPresent();
        }
    }

    @Test
    @DisplayName("UNSPECIFIED (server MỚI HƠN client) trả rỗng, không đoán bừa")
    void unspecifiedEnhanceOutcomeIsUnmapped() {
        assertThat(InventoryController.enhanceMessageKey(EnhanceOutcome.ENHANCE_OUTCOME_UNSPECIFIED))
                .isEmpty();
        assertThat(InventoryController.enhanceMessageKey(EnhanceOutcome.UNRECOGNIZED))
                .isEmpty();
    }

    @Test
    @DisplayName("Mọi khoá i18n của bảng cường hoá có mặt THẬT trong cả hai bundle")
    void everyEnhanceMessageKeyExists() {
        java.util.Properties vietnamese = bundle("strings_vi.properties");
        java.util.Properties english = bundle("strings_en.properties");
        for (EnhanceOutcome outcome : EnhanceOutcome.values()) {
            InventoryController.enhanceMessageKey(outcome).ifPresent(key -> {
                assertThat(vietnamese.getProperty(key))
                        .as("thiếu %s trong bundle vi", key)
                        .isNotNull();
                assertThat(english.getProperty(key))
                        .as("thiếu %s trong bundle en", key)
                        .isNotNull();
            });
        }
    }

    @Test
    @DisplayName("clear() cũng trả Bảo Vệ về TẮT và xoá khoá đang treo")
    void clearResetsEnhanceState() {
        controller.toggleProtection();
        controller.beginEnhance(5L);

        controller.clear();

        assertThat(controller.useProtection()).isFalse();
        assertThat(controller.pendingEnhanceItemId()).isZero();
    }

    // ---------------------------------------------------------------------------------------------
    // Bó buộc giữa i18n và font UI — cùng cặp test MailControllerTest có, mở rộng sang 3 prefix mới
    // (inventory./item./equipment.) thay vì mail.
    // ---------------------------------------------------------------------------------------------

    private static final List<String> NEW_PREFIXES = List.of("inventory.", "item.", "equipment.");

    @Test
    @DisplayName("Mọi chuỗi tiếng Việt của túi đồ/tên vật phẩm/slot nằm trong tập glyph font UI")
    void everyVietnameseInventoryStringIsRenderable() {
        java.util.Properties vietnamese = bundle("strings_vi.properties");
        for (String key : keysWithPrefixes(vietnamese, NEW_PREFIXES)) {
            String value = vietnamese.getProperty(key);
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                assertThat(dev.kage.client.core.render.FontFactory.canRender(character))
                        .as(
                                "%s chứa ký tự '%s' (U+%s) mà font UI KHÔNG vẽ được — chữ sẽ biến mất"
                                        + " lặng lẽ trên màn hình",
                                key, character, Integer.toHexString(character).toUpperCase(java.util.Locale.ROOT))
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("Hai bundle có ĐÚNG cùng tập khoá inventory./item./equipment.")
    void bothBundlesDeclareTheSameInventoryKeys() {
        assertThat(keysWithPrefixes(bundle("strings_vi.properties"), NEW_PREFIXES))
                .isEqualTo(keysWithPrefixes(bundle("strings_en.properties"), NEW_PREFIXES));
    }

    private static java.util.SortedSet<String> keysWithPrefixes(
            java.util.Properties properties, List<String> prefixes) {
        java.util.SortedSet<String> keys = new java.util.TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (prefixes.stream().anyMatch(key::startsWith)) {
                keys.add(key);
            }
        }
        assertThat(keys)
                .as("bundle không có khoá nào trong %s — đường dẫn resource sai?", prefixes)
                .isNotEmpty();
        return keys;
    }

    /** Đọc bundle bằng UTF-8 tường minh: {@code Properties.load(InputStream)} mặc định ISO-8859-1. */
    private static java.util.Properties bundle(String fileName) {
        java.util.Properties properties = new java.util.Properties();
        java.io.InputStream stream = InventoryControllerTest.class.getResourceAsStream("/i18n/" + fileName);
        assertThat(stream)
                .as("không tìm thấy /i18n/%s trên classpath", fileName)
                .isNotNull();
        try (java.io.Reader reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return properties;
    }

    // --- dựng dữ liệu --------------------------------------------------------------------------

    private static ListInventoryResponse list(int capacity, InventoryItemView... items) {
        ListInventoryResponse.Builder builder =
                ListInventoryResponse.newBuilder().setCapacity(capacity);
        for (InventoryItemView view : items) {
            builder.addItems(view);
        }
        return builder.build();
    }

    /**
     * Một item số lượng 1, chưa cường hoá — đủ cho mọi test ngoại trừ
     * {@link #adoptsServerInventory()} (test đó dựng thẳng bằng builder). Không nhận
     * {@code quantity}/{@code enhanceLevel} làm tham số: thêm hai cái đó vào đây là đẩy hàm lên 5
     * tham số — đúng ngưỡng {@code ExcessiveParameterList} của PMD (config/pmd, minimum=5), cùng lý
     * do {@code MailControllerTest#mail(...)} chốt lại ở 4 tham số.
     */
    private static InventoryItemView item(long itemId, long itemTemplateId, int slotIndex) {
        return InventoryItemView.newBuilder()
                .setItemId(itemId)
                .setItemTemplateId(itemTemplateId)
                .setQuantity(1)
                .setEnhanceLevel(0)
                .setSlotIndex(slotIndex)
                .build();
    }

    private static EquippedSlotView equipped(EquipmentSlot slot, long itemId) {
        return EquippedSlotView.newBuilder().setSlot(slot).setItemId(itemId).build();
    }
}
