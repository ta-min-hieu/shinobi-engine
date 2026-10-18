package dev.kage.inventory.domain;

import java.util.Map;

/** Linh Ngọc khảm nạm (ADR-064) — hiện thực ở server-database. */
public interface GemPort {

    /** Rỗng = không có ngọc nào (composition test). */
    GemPort NONE = new GemPort() {
        @Override
        public Map<Long, EquipmentBonus> gemBonuses() {
            return Map.of();
        }

        @Override
        public Map<Long, Long> socketsOf(long characterId) {
            return Map.of();
        }

        @Override
        public int lockFailures(Transaction tx, long characterId) {
            return 0;
        }

        @Override
        public void setFailures(Transaction tx, long characterId, int failures) {
            // không có bảo hiểm khi không có ngọc
        }

        @Override
        public void socket(Transaction tx, long inventoryItemId, long gemItemTemplateId) {
            throw new UnsupportedOperationException("Composition này không có Linh Ngọc");
        }
    };

    /** Mọi loại ngọc và chỉ số của nó, theo {@code item_template.id} của viên ngọc. */
    Map<Long, EquipmentBonus> gemBonuses();

    /** Ô khảm của mọi món trong túi nhân vật: {@code inventory_item.id → item_template.id} của ngọc. */
    Map<Long, Long> socketsOf(long characterId);

    /** Số lần hỏng liên tiếp hiện tại — khoá dòng bảo hiểm của nhân vật (tạo nếu chưa có). */
    int lockFailures(Transaction tx, long characterId);

    void setFailures(Transaction tx, long characterId, int failures);

    /** Khảm (đè ngọc cũ nếu có — D2). */
    void socket(Transaction tx, long inventoryItemId, long gemItemTemplateId);
}
