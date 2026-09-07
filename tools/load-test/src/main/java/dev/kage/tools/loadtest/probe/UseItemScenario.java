package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import dev.kage.protocol.GetItemCatalogRequest;
import dev.kage.protocol.InventoryItemView;
import dev.kage.protocol.ItemCatalogEntry;
import dev.kage.protocol.ItemCatalogResponse;
import dev.kage.protocol.ListInventoryRequest;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.UseItemCommand;
import dev.kage.protocol.UseItemResponse;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Uống MỘT món tiêu hao rồi đọc kết quả thành số (ADR-046 D5) — đường headless DUY NHẤT kiểm được
 * đan dược, vì máu KHÔNG được lưu vào DB: thoát ra vào lại là đầy máu, nên phải đánh mất máu và
 * uống thuốc TRONG CÙNG một phiên.
 *
 * <p><b>Vì sao nhận `code` chứ không nhận `inventory_item.id`</b>: id thật đổi mỗi lần seed lại DB,
 * còn `item_template.code` là khoá nghiệp vụ ổn định — cùng lý do {@code ItemTemplatePort} có
 * {@code findIdByCode}. Probe tra code → templateId qua catalog (509/510), rồi templateId → ô túi
 * qua danh sách túi đồ (507/508). Cả hai đều là opcode ĐÃ CÓ.
 *
 * <p>Máu mới KHÔNG nằm trong {@code UseItemResponse} (ADR-046 D5) — nó tới bằng
 * {@code HealthChangedEvent} (408), thứ mà {@link WorldObservation} đã biết đọc.
 */
final class UseItemScenario {

    /** Ngân sách bỏ qua AoI event khi chờ 1 phản hồi cụ thể — cùng lý do đã ghi ở {@code QuestScenario}. */
    private static final int MAX_FRAMES_TO_SKIP = 2000;

    private final GameConnection connection;
    private final ProbeSession session;

    UseItemScenario(GameConnection connection, ProbeSession session) {
        this.connection = connection;
        this.session = session;
    }

    /** @param itemCode {@code item_template.code}, ví dụ {@code minor_healing_pill} */
    void run(String itemCode) throws IOException {
        Optional<Long> templateId = findTemplateId(itemCode);
        if (templateId.isEmpty()) {
            session.note("KHÔNG tìm thấy '" + itemCode + "' trong catalog vật phẩm — bỏ qua bước uống thuốc");
            return;
        }
        Optional<InventoryItemView> slot = findSlot(templateId.get());
        if (slot.isEmpty()) {
            session.note("Túi đồ KHÔNG có '" + itemCode + "' (templateId=" + templateId.get() + ") — bỏ qua");
            return;
        }

        connection.send(
                Opcode.OPCODE_USE_ITEM_COMMAND_VALUE,
                UseItemCommand.newBuilder()
                        .setInventoryItemId(slot.get().getItemId())
                        .build()
                        .toByteArray());
        UseItemResponse response = UseItemResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_USE_ITEM_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note(String.format(
                Locale.ROOT,
                "dùng %s (itemId=%d): success=%s errorKey='%s' cònLại=%d",
                itemCode,
                slot.get().getItemId(),
                response.getSuccess(),
                response.getErrorKey(),
                response.getRemainingQuantity()));
    }

    private Optional<Long> findTemplateId(String itemCode) throws IOException {
        connection.send(
                Opcode.OPCODE_GET_ITEM_CATALOG_REQUEST_VALUE,
                GetItemCatalogRequest.getDefaultInstance().toByteArray());
        ItemCatalogResponse catalog = ItemCatalogResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_ITEM_CATALOG_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        return catalog.getEntriesList().stream()
                .filter(entry -> itemCode.equals(entry.getCode()))
                .findFirst()
                .map(ItemCatalogEntry::getId);
    }

    private Optional<InventoryItemView> findSlot(long templateId) throws IOException {
        connection.send(
                Opcode.OPCODE_LIST_INVENTORY_REQUEST_VALUE,
                ListInventoryRequest.getDefaultInstance().toByteArray());
        ListInventoryResponse inventory = ListInventoryResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        List<InventoryItemView> items = inventory.getItemsList();
        return items.stream()
                .filter(item -> item.getItemTemplateId() == templateId)
                .findFirst();
    }
}
