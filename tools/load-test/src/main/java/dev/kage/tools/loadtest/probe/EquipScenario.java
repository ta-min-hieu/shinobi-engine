package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import dev.kage.protocol.EquipItemCommand;
import dev.kage.protocol.EquipItemResponse;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.GetItemCatalogRequest;
import dev.kage.protocol.InventoryItemView;
import dev.kage.protocol.ItemCatalogEntry;
import dev.kage.protocol.ItemCatalogResponse;
import dev.kage.protocol.ListInventoryRequest;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Mặc một hoặc nhiều món trang bị rồi đọc kết quả thành số — đường headless DUY NHẤT kiểm được các
 * trận đấu mà phép tính cân bằng GIẢ ĐỊNH người chơi đang mặc một bộ cụ thể.
 *
 * <p><b>Vì sao tồn tại</b>: {@code RaiderBandBalanceTest} tính trận đầu lĩnh trên giả định "mặc bộ
 * Ngoại Môn", nhưng probe trước đây chỉ có {@code --useItemCode} (món TIÊU HAO) nên trận đó chưa
 * bao giờ đo được thật — bàn giao 2026-09-13 ghi nó là "vẫn là giấy" ba lần liên tiếp.
 *
 * <p><b>Vì sao nhận `code` chứ không nhận `item_id` hay slot</b>: cùng lý do đã ghi ở
 * {@link UseItemScenario} — id đổi mỗi lần seed lại DB, còn {@code item_template.code} là khoá
 * nghiệp vụ ổn định. Ô trang bị thì probe KHÔNG bắt người dùng gõ tay: catalog (509/510) đã chở sẵn
 * {@code equip_slot} của từng món, nên suy ra được. Bắt gõ tay vừa dễ sai vừa để client tự quyết
 * thứ mà server mới có quyền quyết (Điều 24 — server vẫn kiểm lại, đây chỉ là chuyện đừng đoán bừa).
 *
 * <p><b>Thứ tự gọi có ý nghĩa</b>: phải chạy TRƯỚC bước đi bộ. {@code bonus_hp} nâng MÁU TỐI ĐA
 * thật (ADR-036) và server trả máu mới ngay trong {@code EquipItemResponse} (ADR-043), nên mặc đồ
 * sau khi đã đi thì mọi số đo "máu lúc tới nơi" đều sai — ngược hẳn với {@link UseItemScenario},
 * thứ cố tình chạy SAU vòng quan sát.
 */
final class EquipScenario {

    /** Ngân sách bỏ qua AoI event khi chờ 1 phản hồi cụ thể — cùng lý do đã ghi ở {@code QuestScenario}. */
    private static final int MAX_FRAMES_TO_SKIP = 2000;

    private final GameConnection connection;
    private final ProbeSession session;

    EquipScenario(GameConnection connection, ProbeSession session) {
        this.connection = connection;
        this.session = session;
    }

    /** @param itemCodes danh sách {@code item_template.code}, ví dụ {@code outer_sect_blade} */
    void run(List<String> itemCodes) throws IOException {
        List<ItemCatalogEntry> catalog = readCatalog();
        List<InventoryItemView> bag = readInventory();
        for (String itemCode : itemCodes) {
            equipOne(itemCode, catalog, bag);
        }
    }

    private void equipOne(String itemCode, List<ItemCatalogEntry> catalog, List<InventoryItemView> bag)
            throws IOException {
        Optional<ItemCatalogEntry> entry =
                catalog.stream().filter(item -> itemCode.equals(item.getCode())).findFirst();
        if (entry.isEmpty()) {
            session.note("KHÔNG tìm thấy '" + itemCode + "' trong catalog vật phẩm — bỏ qua");
            return;
        }
        EquipmentSlot slot = entry.get().getEquipSlot();
        if (slot == EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED) {
            // UNSPECIFIED = equip_slot NULL trong DB, tức món này KHÔNG mặc được (vd thẻ bài quest
            // `outer_sect_tally`). Nói thẳng thay vì gửi lệnh chắc chắn bị server từ chối.
            session.note("'" + itemCode + "' không phải trang bị (equip_slot UNSPECIFIED) — bỏ qua");
            return;
        }
        Optional<InventoryItemView> owned = bag.stream()
                .filter(item -> item.getItemTemplateId() == entry.get().getId())
                .findFirst();
        if (owned.isEmpty()) {
            session.note("Túi đồ KHÔNG có '" + itemCode + "' (templateId="
                    + entry.get().getId() + ") — bỏ qua");
            return;
        }

        connection.send(
                Opcode.OPCODE_EQUIP_ITEM_COMMAND_VALUE,
                EquipItemCommand.newBuilder()
                        .setItemId(owned.get().getItemId())
                        .setSlot(slot)
                        .build()
                        .toByteArray());
        EquipItemResponse response = EquipItemResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_EQUIP_ITEM_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note(String.format(
                Locale.ROOT,
                "mặc %s (itemId=%d, ô %s): success=%s errorKey='%s' máu=%d/%d",
                itemCode,
                owned.get().getItemId(),
                slot.name(),
                response.getSuccess(),
                response.getErrorKey(),
                response.getCurrentHp(),
                response.getMaxHp()));
    }

    private List<ItemCatalogEntry> readCatalog() throws IOException {
        connection.send(
                Opcode.OPCODE_GET_ITEM_CATALOG_REQUEST_VALUE,
                GetItemCatalogRequest.getDefaultInstance().toByteArray());
        return ItemCatalogResponse.parseFrom(connection
                        .readUntilOpcode(Opcode.OPCODE_ITEM_CATALOG_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                        .payload())
                .getEntriesList();
    }

    private List<InventoryItemView> readInventory() throws IOException {
        connection.send(
                Opcode.OPCODE_LIST_INVENTORY_REQUEST_VALUE,
                ListInventoryRequest.getDefaultInstance().toByteArray());
        return ListInventoryResponse.parseFrom(connection
                        .readUntilOpcode(Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                        .payload())
                .getItemsList();
    }
}
