package dev.kage.server.gateway;

import dev.kage.inventory.domain.EquipmentBonus;
import dev.kage.server.world.EquipmentBonusSnapshot;

/** Dòng tẩy luyện của các món đang mặc (ADR-111 D3) cộng phẳng vào ống chỉ số trang bị, cùng khuôn sổ sưu tầm. */
final class RefinementEffects {

    private RefinementEffects() {}

    /** {@code bonus} cộng chỉ số tẩy luyện. */
    static EquipmentBonusSnapshot plus(EquipmentBonusSnapshot bonus, EquipmentBonus refinement) {
        if (refinement.equals(EquipmentBonus.NONE)) {
            return bonus;
        }
        return bonus.withStats(
                bonus.atk() + refinement.bonusAtk(),
                bonus.def() + refinement.bonusDef(),
                bonus.res() + refinement.bonusRes(),
                bonus.hp() + refinement.bonusHp());
    }
}
