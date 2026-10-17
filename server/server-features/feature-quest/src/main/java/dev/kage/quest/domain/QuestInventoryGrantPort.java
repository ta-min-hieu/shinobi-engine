package dev.kage.quest.domain;

import java.util.List;

/**
 * Cổng cấp item thưởng quest vào túi đồ ({@code inventory_item}, thuộc feature-inventory) — lý do
 * khai báo riêng giống hệt {@link ExpPort}. Nhận {@code itemTemplateCode} (không phải ID số) vì
 * script chỉ biết mã item ổn định (VD {@code "kunai"}) — adapter thật tự tra
 * {@code item_template.id} theo code nội bộ, feature-quest domain không cần biết ID số.
 */
public interface QuestInventoryGrantPort {

    /**
     * Xếp hàng sau MỌI transaction khác đang ghi túi đồ của {@code characterId}, giữ tới hết
     * {@code tx}. PHẢI là lời gọi ĐẦU TIÊN trong transaction lĩnh thưởng, trước EXP (cùng dòng
     * {@code character_stat} với vàng) — thứ tự khoá chung, xem DATABASE.md §5.1 "Thứ tự
     * khoá túi đồ". Sau khoá này {@link #hasRoomFor} mới trả lời đúng cho tới lúc commit.
     *
     * <p>Mặc định không làm gì CHỈ để các fake ở test khỏi phải sửa — adapter thật PHẢI ghi đè.
     */
    default void lockInventory(Transaction tx, long characterId) {
        // Fake trong test: không có transaction thật, không có gì để khoá.
    }

    /**
     * {@code false} nếu {@code itemTemplateCode} không tồn tại HOẶC túi đầy (không đổi gì). Từ
     * 2026-10-02 {@code ClaimQuestRewardUseCase} coi {@code false} là lỗi cứng: rollback cả lượt
     * lĩnh và trả {@code INVENTORY_FULL}.
     */
    boolean grantItemByCode(Transaction tx, long characterId, String itemTemplateCode, int quantity);

    /**
     * Túi còn đủ ô cho TRỌN lô phần thưởng này không — hỏi TRƯỚC khi ghi bất cứ thứ gì.
     *
     * <p><b>Vì sao tồn tại</b> (bug đo thật 2026-09-13): {@link #grantItemByCode} trả {@code false}
     * khi túi đầy, nhưng {@code ClaimQuestRewardUseCase} VỨT BỎ giá trị đó — quest 11 khai trao 6
     * món, túi chỉ nhận 5, mà response vẫn OK, status vẫn thành CLAIMED và {@code idempotency_key}
     * vẫn bị đốt ⇒ MẤT MÓN VĨNH VIỄN. Túi đầy KHÔNG ném exception ({@code Inventory.add} coi đó là
     * trạng thái bình thường) nên guard {@code scriptFailed} không che được ca này.
     *
     * <p><b>Là {@code default} có chủ đích</b>: interface này đang được dùng như functional
     * interface ở nhiều test ({@code (tx, id, code, qty) -> true}). Giữ đúng MỘT abstract method thì
     * những lambda đó vẫn biên dịch, và implementer chưa quan tâm giữ nguyên hành vi cũ.
     *
     * <p><b>Nhận {@link Transaction}</b> để adapter dùng lại đúng đường lấy {@code Connection} sẵn
     * có, khỏi phải thêm {@code DataSource} vào constructor (5 nơi đang dựng nó không tham số).
     * Người gọi hỏi NGAY TRONG transaction ghi, sau {@link #lockInventory} (sửa 2026-09-25 — trước
     * đó hỏi ở một transaction chỉ đọc riêng, nên một lần mua hàng chen giữa hai transaction làm
     * câu trả lời "đủ chỗ" thành sai lúc trao).
     *
     * @param grants danh sách {@code give_item} theo đúng thứ tự script gọi
     * @return {@code true} nếu đủ chỗ (mặc định) — chỉ {@code false} khi chắc chắn thiếu ô
     */
    default boolean hasRoomFor(Transaction tx, long characterId, List<ItemGrantRequest> grants) {
        return true;
    }

    /**
     * Mã vật phẩm có trong {@code item_template} không (dòng 77(a), 2026-10-17). Trước đó mã sai đi
     * thẳng vào {@link #grantItemByCode} — trả {@code false} y như túi đầy — nên người chơi đọc "túi
     * đầy" trong khi túi trống; lỗi thật là của SCRIPT. Hỏi trước, trong cùng transaction lĩnh.
     *
     * <p>Mặc định {@code true} — cùng lý do {@link #hasRoomFor} là {@code default}.
     */
    default boolean itemExists(Transaction tx, String itemTemplateCode) {
        return true;
    }

    /** Một lời {@code give_item} của script — tách riêng để domain không phụ thuộc ngược vào application. */
    record ItemGrantRequest(String itemTemplateCode, int quantity) {}
}
