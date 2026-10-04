package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import dev.kage.protocol.BuyItemCommand;
import dev.kage.protocol.BuyItemResponse;
import dev.kage.protocol.GetItemCatalogRequest;
import dev.kage.protocol.InventoryItemView;
import dev.kage.protocol.ItemCatalogEntry;
import dev.kage.protocol.ItemCatalogResponse;
import dev.kage.protocol.ListInventoryRequest;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SellItemCommand;
import dev.kage.protocol.SellItemResponse;
import dev.kage.tools.loadtest.GameConnection;

/**
 * BÁN rồi MUA ở cửa hàng — đường headless duy nhất đo được câu hỏi "một nhân vật Cấp 1 mới tinh
 * kiếm đâu ra vàng mua bộ Tạp Dịch mà lớp cân bằng quest 2–4 giả định" bằng số thật.
 *
 * <p><b>Vì sao tồn tại</b> (kiểm sống chuỗi quest arc 1, 2026-10-03): nhân vật mới vào world với
 * {@code gold=0}, không quest nào thưởng vàng, quái không rơi vàng — nguồn vàng DUY NHẤT là bán đồ
 * rơi ở cửa hàng. Trước bước này probe không có đường bán/mua, nên cách duy nhất để "có bộ Tạp Dịch"
 * là cấp đồ bằng SQL, tức nhảy qua đúng chỗ đang cần kiểm.
 *
 * <p>Thứ tự trong một lượt: BÁN hết danh sách bán TRƯỚC, rồi mới MUA — đúng thứ tự một người chơi
 * đổi đồ rơi lấy trang bị. Chạy TRƯỚC {@link EquipScenario} trong cùng lượt để món vừa mua mặc được
 * ngay. Nhận {@code item_template.code} (như {@link EquipScenario}), không nhận id.
 *
 * <p>Cú pháp mỗi phần tử: {@code code} hoặc {@code code:soLuong}. Bán không ghi số lượng = bán HẾT
 * mọi chồng của món đó; mua không ghi số lượng = mua 1.
 */
final class ShopScenario {

    /** Ngân sách bỏ qua AoI event khi chờ 1 phản hồi cụ thể — cùng lý do đã ghi ở {@code QuestScenario}. */
    private static final int MAX_FRAMES_TO_SKIP = 2000;

    /** Số lượng "tất cả" của một dòng bán không ghi số lượng. */
    static final int ALL = -1;

    /**
     * Một dòng mua/bán đã parse.
     *
     * @param code {@code item_template.code}
     * @param quantity số lượng, hoặc {@link #ALL}
     */
    record Line(String code, int quantity) {

        /**
         * Parse {@code code} hoặc {@code code:soLuong}.
         *
         * @param defaultQuantity số lượng khi không ghi ({@link #ALL} cho bán, 1 cho mua)
         * @throws NumberFormatException nếu phần sau dấu hai chấm không phải số dương — gõ sai thì
         *     dừng ngay, đừng âm thầm mua/bán một số lượng khác ý người dùng
         */
        static Line parse(String raw, int defaultQuantity) {
            int colon = raw.indexOf(':');
            if (colon < 0) {
                return new Line(raw, defaultQuantity);
            }
            int quantity = Integer.parseInt(raw.substring(colon + 1));
            if (quantity <= 0) {
                throw new NumberFormatException("Số lượng phải dương: '" + raw + "'");
            }
            return new Line(raw.substring(0, colon), quantity);
        }
    }

    private final GameConnection connection;
    private final ProbeSession session;

    ShopScenario(GameConnection connection, ProbeSession session) {
        this.connection = connection;
        this.session = session;
    }

    /**
     * @param sellLines các dòng bán ({@code code[:soLuong]})
     * @param buyLines các dòng mua ({@code code[:soLuong]})
     */
    void run(List<String> sellLines, List<String> buyLines) throws IOException {
        List<ItemCatalogEntry> catalog = readCatalog();
        for (String raw : sellLines) {
            sell(Line.parse(raw, ALL), catalog);
        }
        for (String raw : buyLines) {
            buy(Line.parse(raw, 1), catalog);
        }
    }

    private void sell(Line line, List<ItemCatalogEntry> catalog) throws IOException {
        Optional<ItemCatalogEntry> entry = find(line.code(), catalog);
        if (entry.isEmpty()) {
            return;
        }
        int remaining = line.quantity() == ALL ? Integer.MAX_VALUE : line.quantity();
        List<InventoryItemView> stacks = new ArrayList<>();
        for (InventoryItemView item : readInventory()) {
            if (item.getItemTemplateId() == entry.get().getId()) {
                stacks.add(item);
            }
        }
        if (stacks.isEmpty()) {
            session.note("bán: túi KHÔNG có '" + line.code() + "' — bỏ qua");
            return;
        }
        for (InventoryItemView stack : stacks) {
            if (remaining <= 0) {
                break;
            }
            int quantity = Math.min(remaining, stack.getQuantity());
            connection.send(
                    Opcode.OPCODE_SELL_ITEM_COMMAND_VALUE,
                    SellItemCommand.newBuilder()
                            .setItemId(stack.getItemId())
                            .setQuantity(quantity)
                            .setIdempotencyKey(UUID.randomUUID().toString())
                            .build()
                            .toByteArray());
            SellItemResponse response = SellItemResponse.parseFrom(connection
                    .readUntilOpcode(Opcode.OPCODE_SELL_ITEM_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                    .payload());
            session.note(String.format(
                    Locale.ROOT,
                    "bán %s x%d (itemId=%d): %s",
                    line.code(),
                    quantity,
                    stack.getItemId(),
                    response.getOutcome().name()));
            remaining -= quantity;
        }
    }

    private void buy(Line line, List<ItemCatalogEntry> catalog) throws IOException {
        Optional<ItemCatalogEntry> entry = find(line.code(), catalog);
        if (entry.isEmpty()) {
            return;
        }
        connection.send(
                Opcode.OPCODE_BUY_ITEM_COMMAND_VALUE,
                BuyItemCommand.newBuilder()
                        .setItemTemplateId(entry.get().getId())
                        .setQuantity(line.quantity())
                        .setIdempotencyKey(UUID.randomUUID().toString())
                        .build()
                        .toByteArray());
        BuyItemResponse response = BuyItemResponse.parseFrom(connection
                .readUntilOpcode(Opcode.OPCODE_BUY_ITEM_RESPONSE_VALUE, MAX_FRAMES_TO_SKIP)
                .payload());
        session.note(String.format(
                Locale.ROOT,
                "mua %s x%d: %s",
                line.code(),
                line.quantity(),
                response.getOutcome().name()));
    }

    private Optional<ItemCatalogEntry> find(String code, List<ItemCatalogEntry> catalog) {
        Optional<ItemCatalogEntry> entry =
                catalog.stream().filter(item -> code.equals(item.getCode())).findFirst();
        if (entry.isEmpty()) {
            session.note("KHÔNG tìm thấy '" + code + "' trong catalog vật phẩm — bỏ qua");
        }
        return entry;
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
