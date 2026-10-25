package dev.kage.server.gateway;

import dev.kage.achievement.domain.BestiaryPort;
import dev.kage.server.world.EquipmentBonusSnapshot;

/** Trang sưu tầm đã mở (ADR-108 D3) quy ra chỉ số cộng phẳng đi chung ống chỉ số trang bị, cùng khuôn Đạo Vận. */
final class BestiaryEffects {

    private BestiaryEffects() {}

    /** {@code bonus} cộng thưởng sổ sưu tầm. */
    static EquipmentBonusSnapshot plus(EquipmentBonusSnapshot bonus, BestiaryPort.Bonus bestiary) {
        if (bestiary.equals(BestiaryPort.Bonus.NONE)) {
            return bonus;
        }
        return bonus.withStats(
                bonus.atk() + bestiary.atk(),
                bonus.def() + bestiary.def(),
                bonus.res() + bestiary.res(),
                bonus.hp() + bestiary.hp());
    }
}
