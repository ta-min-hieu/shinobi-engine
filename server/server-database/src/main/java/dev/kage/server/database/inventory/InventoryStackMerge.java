package dev.kage.server.database.inventory;

import java.sql.Connection;

import dev.kage.server.database.JdbcOperations;

/**
 * Gộp vật phẩm vào một chồng sẵn có, cho những đường cấp đồ đi THẲNG bằng SQL.
 *
 * <p><b>Vì sao tồn tại (2026-09-05).</b> Dự án có NHIỀU đường cấp vật phẩm vào túi, không phải một:
 *
 * <ol>
 *   <li>{@code Inventory.add()} — rơi đồ, {@code AddItemUseCase}. Luật xếp chồng nằm ở domain.
 *   <li>{@code PostgresShopInventoryGatewayAdapter.grantItem} — MUA hàng, {@code INSERT} thẳng.
 *   <li>{@code PostgresQuestInventoryGrantAdapter.grantItemByCode} — thưởng nhiệm vụ, {@code INSERT} thẳng.
 *   <li>{@code PostgresMailInventoryGatewayAdapter.grantItem} — nhận thư, {@code INSERT} thẳng. Bị
 *       BỎ SÓT lúc bản vá đầu (chỉ đếm ba đường), thêm 2026-09-24.
 *   <li>{@code PostgresTradeInventoryAdapter.transferItem} — giao dịch, đổi chủ/tách dòng thẳng.
 *       Trước 2026-09-24 CỐ Ý không gộp (quyết định MVP 2 của feature-trade, 2026-07-24); chủ dự án
 *       đảo quyết định đó ngày 2026-09-24 vì lý do gốc ("không đường nào khác gộp") đã sai.
 * </ol>
 *
 * <p>Hai đường sau bỏ qua hẳn domain (feature-shop/feature-quest không được import feature-inventory
 * — mỗi feature chỉ đọc bảng của mình qua adapter riêng, xem server/CLAUDE.md). Sửa xếp chồng ở
 * domain mà quên hai đường này thì mua/nhận thưởng vẫn ăn ô mới — playtest 2026-09-05 bắt đúng
 * cảnh đó: sửa xong domain, mua thêm 1 phi đao vẫn đẻ ra dòng thứ mười.
 *
 * <p>Luật xếp chồng của chủ dự án nằm sẵn ở {@code item_template.max_stack}: trang bị (mọi thứ có
 * {@code equip_slot}) là 1, còn tiêu hao/nguyên liệu/vật phẩm nhiệm vụ là 99. Câu SQL dưới đây ĐỌC
 * cột đó thay vì nhúng luật vào Java, nên thêm loại vật phẩm mới không phải sửa code.
 */
public final class InventoryStackMerge {

    /**
     * Cộng vào chồng CŨ đủ chỗ, chọn dòng id nhỏ nhất cho tất định.
     *
     * <p>{@code enhance_level = 0} ở cả hai vế: chỉ trang bị mới cường hoá được, mà trang bị thì
     * {@code max_stack = 1} nên không bao giờ vào nhánh này — điều kiện này là để đúng NGHĨA, không
     * dựa vào sự trùng hợp đó.
     *
     * <p>{@code max_stack > 1} lọc thẳng trong SQL: item không xếp chồng thì không bao giờ khớp.
     *
     * <p>Postgres không cho {@code LIMIT} trực tiếp trong {@code UPDATE} — phải qua truy vấn con.
     *
     * <p>{@code ii.quantity <= max_stack - ?} chứ không {@code ii.quantity + ? <= max_stack} (sửa
     * 2026-10-03): số lượng tới từ client ({@code int32}), cộng gần {@code Integer.MAX_VALUE} thì
     * Postgres nổ "integer out of range" ⇒ 500. Phép trừ không tràn vì {@code max_stack} dương.
     */
    private static final String MERGE_INTO_EXISTING_STACK = """
            UPDATE inventory_item SET quantity = quantity + ?
             WHERE id = (SELECT ii.id
                           FROM inventory_item ii
                           JOIN item_template it ON it.id = ii.item_template_id
                          WHERE ii.character_id = ?
                            AND ii.item_template_id = ?
                            AND ii.enhance_level = 0
                            AND it.max_stack > 1
                            AND ii.quantity <= it.max_stack - ?
                          ORDER BY ii.id
                          LIMIT 1)
            """;

    private static final String FIND_MAX_STACK = """
            SELECT max_stack FROM item_template WHERE id = ?
            """;

    // Chỉ đếm món NẰM TRONG TÚI: đồ đang mặc không chiếm ô (quyết định chủ dự án 2026-10-11). Trước đó
    // túi 20 ô tính cả 7 món trên người, nên người mặc đủ bộ chỉ còn 13 ô thật.
    private static final String COUNT_ITEMS = """
            SELECT COUNT(*) FROM inventory_item ii
             WHERE ii.character_id = ?
               AND NOT EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = ii.id)
            """;

    // ADR-160 — dòng mới luôn nối CUỐI túi (MAX + 1): túi nay liệt kê theo slot_index, và "số dòng đang có" (cách cũ)
    // nhỏ hơn slot cuối khi có đồ đang mặc hay lỗ sau khi dòng bị xoá — dòng mới sẽ chen vào giữa túi.
    private static final String INSERT_ITEM = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
            VALUES (?, ?, ?, ?, (SELECT COALESCE(MAX(slot_index) + 1, 0) FROM inventory_item WHERE character_id = ?))
            """;

    private InventoryStackMerge() {}

    /**
     * Thử gộp trước khi mở ô mới.
     *
     * @return {@code true} nếu đã cộng vào một chồng sẵn có ⇒ người gọi KHÔNG được {@code INSERT}
     *     nữa, và cũng không cần kiểm sức chứa túi (gộp không tốn ô). {@code false} nếu không có
     *     chồng nào nhận được — người gọi đi tiếp đường {@code INSERT} như cũ.
     */
    public static boolean mergeIntoExistingStack(
            Connection connection, long characterId, long itemTemplateId, int quantity) {
        return JdbcOperations.update(
                        connection, MERGE_INTO_EXISTING_STACK, quantity, characterId, itemTemplateId, quantity)
                > 0;
    }

    /**
     * Số ô mới cần để chứa {@code quantity} món của loại này khi KHÔNG gộp được: chia theo
     * {@code max_stack} (làm tròn lên). Loại không tồn tại ⇒ 1, để người gọi không đoán sai là "vừa".
     */
    public static int slotsNeeded(Connection connection, long itemTemplateId, int quantity) {
        long rows = rowsNeeded(quantity, maxStackOf(connection, itemTemplateId));
        return (int) Math.min(Integer.MAX_VALUE, rows);
    }

    /**
     * Mở ô mới cho {@code quantity} món, mỗi dòng KHÔNG vượt {@code max_stack}; không đủ ô thì không
     * ghi gì cả.
     *
     * <p><b>Vì sao tồn tại (2026-09-24).</b> Trước đó shop/thư/quest {@code INSERT} MỘT dòng mang
     * nguyên số lượng yêu cầu khi không gộp được. Cửa hàng có ô nhập số lượng ngay trên client hợp
     * lệ: gõ "50" khi mua kiếm gỗ ({@code max_stack = 1}) là được một dòng ×50, và cường hoá giữ
     * nguyên {@code quantity} của dòng — cường hoá 50 thanh với giá một lần. Phi đao thì một ô chứa
     * cả nghìn cái, vượt trần 99. {@link #mergeIntoExistingStack} đã tôn trọng {@code max_stack} từ
     * đầu; nửa còn lại (mở ô mới) thì chưa.
     *
     * @return {@code false} nếu túi không đủ ô cho số dòng cần mở — không có dòng nào được ghi,
     *     người gọi báo túi đầy như cũ.
     */
    public static boolean insertRespectingMaxStack(
            Connection connection, long characterId, long itemTemplateId, int quantity) {
        return insertRespectingMaxStack(connection, characterId, new ItemLine(itemTemplateId, quantity, 0));
    }

    /** Như trên, các dòng mới mang cấp cường hoá của {@code line} (thư chở đồ cường hoá — ADR-083). */
    public static boolean insertRespectingMaxStack(Connection connection, long characterId, ItemLine line) {
        long itemTemplateId = line.itemTemplateId();
        int quantity = line.quantity();
        int enhanceLevel = line.enhanceLevel();
        int maxStack = maxStackOf(connection, itemTemplateId);
        long rowsNeeded = rowsNeeded(quantity, maxStack);
        int used = JdbcOperations.queryOne(connection, COUNT_ITEMS, rs -> rs.getInt(1), characterId)
                .orElse(0);
        if (used + rowsNeeded > BagCapacity.of(connection, characterId)) {
            return false;
        }
        int remaining = quantity;
        for (int row = 0; row < rowsNeeded; row++) {
            int rowQuantity = Math.min(maxStack, remaining);
            // slot_index — nối vào cuối túi (ADR-160, xem INSERT_ITEM).
            JdbcOperations.update(
                    connection, INSERT_ITEM, characterId, itemTemplateId, rowQuantity, enhanceLevel, characterId);
            remaining -= rowQuantity;
        }
        return true;
    }

    // Tính bằng long (sửa 2026-10-03): quantity tới từ client, `quantity + maxStack - 1` sát
    // Integer.MAX_VALUE tràn thành số ÂM ⇒ "cần −21 ô, vừa" ⇒ không chèn gì mà vẫn báo thành công
    // (cửa hàng đã trừ tiền). Kết quả có thể vượt int — người gọi chỉ so với sức chứa túi.
    private static long rowsNeeded(int quantity, int maxStack) {
        return ((long) quantity + maxStack - 1) / maxStack;
    }

    // max_stack < 1 không hợp lệ theo dữ liệu hiện có, nhưng chia cho 0 ở đây là sập cả giao dịch —
    // coi như 1 (mỗi món một ô) là phía an toàn.
    private static int maxStackOf(Connection connection, long itemTemplateId) {
        int maxStack = JdbcOperations.queryOne(connection, FIND_MAX_STACK, rs -> rs.getInt(1), itemTemplateId)
                .orElse(1);
        return Math.max(1, maxStack);
    }
}
