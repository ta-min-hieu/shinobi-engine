// TEMPLATE — copy vào feature module thật, đổi dev.kage → package root thật.
// Value Object: record immutable, validate trong compact constructor (Fail Fast, Điều 16).
package dev.kage.inventory.domain;

/**
 * Một stack item trong túi đồ.
 *
 * <p>Value Object bất biến. Không tồn tại ở trạng thái không hợp lệ —
 * mọi ràng buộc kiểm ngay khi tạo (Điều 16 Fail Fast).
 */
public record ItemStack(long itemTemplateId, int quantity, int enhanceLevel) {

    public static final int MAX_ENHANCE = 15;

    public ItemStack {
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

    /** Trả về stack mới với số lượng cộng thêm — bất biến, không sửa this. */
    public ItemStack plus(int amount) {
        return new ItemStack(itemTemplateId, quantity + amount, enhanceLevel);
    }
}
