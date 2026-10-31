package dev.kage.server.gateway;

import dev.kage.character.domain.TribulationRules;
import dev.kage.server.world.EquipmentBonusSnapshot;

/**
 * Thưởng Thiên Kiếp (ADR-126) quy ra chỉ số cộng phẳng đi chung ống chỉ số trang bị — mỗi kiếp đã qua cộng công/thủ/
 * kháng + máu tối đa, vĩnh viễn (chuyển thế thì về 0 cùng số kiếp).
 */
final class TribulationEffects {

    private TribulationEffects() {}

    /** {@code bonus} cộng thưởng của {@code passed} kiếp đã qua. */
    static EquipmentBonusSnapshot plus(EquipmentBonusSnapshot bonus, int passed) {
        if (passed <= 0) {
            return bonus;
        }
        return bonus.withStats(
                bonus.atk() + TribulationRules.BONUS_ATK * passed,
                bonus.def() + TribulationRules.BONUS_DEF * passed,
                bonus.res() + TribulationRules.BONUS_RES * passed,
                bonus.hp() + TribulationRules.BONUS_HP * passed);
    }
}
