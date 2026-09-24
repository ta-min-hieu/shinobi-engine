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
                            AND ii.quantity + ? <= it.max_stack
                          ORDER BY ii.id
                          LIMIT 1)
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
}
