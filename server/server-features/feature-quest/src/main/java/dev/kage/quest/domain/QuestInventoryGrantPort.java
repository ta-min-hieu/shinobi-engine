package dev.kage.quest.domain;

import java.util.List;

/**
 * Cổng cấp item thưởng quest vào túi đồ ({@code inventory_item}, thuộc feature-inventory) — lý do
 * khai báo riêng giống hệt {@link ExpPort}. Nhận {@code itemTemplateCode} (không phải ID số) vì
 * script chỉ biết mã item ổn định (VD {@code "kunai"}) — adapter thật tự tra
 * {@code item_template.id} theo code nội bộ, feature-quest domain không cần biết ID số.
 */
public interface QuestInventoryGrantPort {

    /** {@code false} nếu {@code itemTemplateCode} không tồn tại HOẶC túi đầy (không đổi gì). */
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
     * Người gọi mở một transaction CHỈ ĐỌC riêng cho câu hỏi này, trước transaction ghi.
     *
     * @param grants danh sách {@code give_item} theo đúng thứ tự script gọi
     * @return {@code true} nếu đủ chỗ (mặc định) — chỉ {@code false} khi chắc chắn thiếu ô
     */
    default boolean hasRoomFor(Transaction tx, long characterId, List<ItemGrantRequest> grants) {
        return true;
    }

    /** Một lời {@code give_item} của script — tách riêng để domain không phụ thuộc ngược vào application. */
    record ItemGrantRequest(String itemTemplateCode, int quantity) {}
}
