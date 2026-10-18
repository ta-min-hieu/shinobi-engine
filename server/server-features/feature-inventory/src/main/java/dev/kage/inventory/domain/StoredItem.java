package dev.kage.inventory.domain;

/**
 * Một dòng trong kho đồ tông môn (ADR-061, bảng {@code storage_item}).
 *
 * @param id {@code storage_item.id}; 0 = chưa lưu
 */
public record StoredItem(long id, long itemTemplateId, int quantity, int enhanceLevel) {

    public StoredItem {
        if (quantity < 1 || enhanceLevel < 0) {
            throw new IllegalArgumentException("Dòng kho sai dạng: quantity=" + quantity);
        }
    }

    /** Cùng loại + cùng cấp cường hoá + còn đủ chỗ trong chồng — cùng luật {@link Inventory#add}. */
    public boolean canMerge(long templateId, int level, int addQuantity, int maxStack) {
        return maxStack > 1
                && itemTemplateId == templateId
                && enhanceLevel == level
                && quantity + addQuantity <= maxStack;
    }
}
