package dev.kage.inventory.domain;

/**
 * Linh thú đang mặc sau một lần nhận EXP (ADR-072).
 *
 * @param inventoryItemId dòng túi đồ của con linh thú
 * @param itemTemplateId mẫu (tên hiển thị)
 * @param level cấp mới
 * @param expIntoLevel EXP đã có trong cấp hiện tại
 * @param leveledUp lần nhận này làm nó lên cấp
 */
public record PetProgress(long inventoryItemId, long itemTemplateId, int level, long expIntoLevel, boolean leveledUp) {

    /** EXP cần cho cấp kế (0 = tối đa). */
    public long expToNext() {
        return PetPolicy.expToNext(level);
    }
}
