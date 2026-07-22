package dev.kage.inventory.domain;

/**
 * Một stack item trong túi đồ. Value Object bất biến — không tồn tại ở trạng thái không hợp lệ,
 * mọi ràng buộc kiểm ngay khi tạo (Điều 16 Fail Fast).
 *
 * <p>{@code id} là {@code inventory_item.id} THẬT (DB tự cấp qua {@code RETURNING id}) — dùng để
 * tham chiếu ĐÚNG 1 item cụ thể (VD {@code equipment_slot.inventory_item_id}, Phase 5 "mặc đồ").
 * {@link #NEW_ITEM_ID} (= 0) nghĩa là item CHƯA được lưu, chưa có ID thật — dùng
 * {@link #newItem(long, int, int)} khi tạo item mới (VD rơi đồ, nhặt đồ) thay vì gọi constructor
 * trực tiếp với id giả.
 */
public record ItemStack(long id, long itemTemplateId, int quantity, int enhanceLevel) {

    public static final int MAX_ENHANCE = 15;

    /** {@code id} chưa gán — chỉ dùng nội bộ trước khi {@code save()} thật vào DB. */
    public static final long NEW_ITEM_ID = 0L;

    public ItemStack {
        if (id < 0) {
            throw new IllegalArgumentException("id không được âm: " + id);
        }
        if (itemTemplateId <= 0) {
            throw new IllegalArgumentException("itemTemplateId phải > 0: " + itemTemplateId);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity phải > 0: " + quantity);
        }
        if (enhanceLevel < 0 || enhanceLevel > MAX_ENHANCE) {
            throw new IllegalArgumentException("enhanceLevel ngoài [0," + MAX_ENHANCE + "]: " + enhanceLevel);
        }
    }

    /** Item MỚI, chưa từng lưu DB — {@code id} sẽ được adapter gán thật lúc {@code save()}. */
    public static ItemStack newItem(long itemTemplateId, int quantity, int enhanceLevel) {
        return new ItemStack(NEW_ITEM_ID, itemTemplateId, quantity, enhanceLevel);
    }

    public boolean isPersisted() {
        return id != NEW_ITEM_ID;
    }
}
