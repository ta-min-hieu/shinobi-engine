package dev.kage.monster.domain;

/**
 * Một bậc thưởng theo hạng sát thương (ADR-081 D4) — bảng {@code world_boss_reward}. Hạng tính từ 1.
 *
 * @param rankFrom hạng đầu (gồm)
 * @param rankTo hạng cuối (gồm)
 * @param gold linh thạch
 * @param itemTemplateId mẫu vật phẩm, 0 nếu chỉ thưởng linh thạch
 * @param quantity số lượng vật phẩm
 */
public record WorldBossReward(int rankFrom, int rankTo, long gold, long itemTemplateId, int quantity) {

    /** Bậc này có vật phẩm. */
    public boolean hasItem() {
        return itemTemplateId > 0 && quantity > 0;
    }
}
