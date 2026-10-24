package dev.kage.server.gateway;

import dev.kage.character.domain.RebirthPolicy;
import dev.kage.server.world.EquipmentBonusSnapshot;

/**
 * Đạo Vận (ADR-089) quy ra chỉ số cộng phẳng đi chung ống chỉ số trang bị — mỗi lần chuyển thế cộng máu tối đa và
 * công/thủ/kháng, vĩnh viễn.
 */
final class RebirthEffects {

    private RebirthEffects() {}

    /** {@code bonus} cộng Đạo Vận của {@code rebirthCount} lần chuyển thế. */
    static EquipmentBonusSnapshot plus(EquipmentBonusSnapshot bonus, int rebirthCount) {
        if (rebirthCount <= 0) {
            return bonus;
        }
        int stat = RebirthPolicy.STAT_PER_REBIRTH * rebirthCount;
        return bonus.withStats(
                bonus.atk() + stat,
                bonus.def() + stat,
                bonus.res() + stat,
                bonus.hp() + RebirthPolicy.HP_PER_REBIRTH * rebirthCount);
    }
}
