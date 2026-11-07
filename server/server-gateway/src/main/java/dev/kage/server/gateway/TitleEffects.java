package dev.kage.server.gateway;

import dev.kage.achievement.domain.TitleBonusPort;
import dev.kage.server.world.EquipmentBonusSnapshot;

/**
 * Danh hiệu đang đeo (ADR-210) quy ra chỉ số cộng phẳng đi chung ống chỉ số trang bị — cùng khuôn {@link
 * TribulationEffects}: vào world, mặc/gỡ đồ, đổi danh hiệu, bảng nhân vật đều thấy cùng một con số.
 */
final class TitleEffects {

    private TitleEffects() {}

    /** {@code bonus} cộng chỉ số của danh hiệu {@code title}. */
    static EquipmentBonusSnapshot plus(EquipmentBonusSnapshot bonus, TitleBonusPort.TitleBonus title) {
        if (title.equals(TitleBonusPort.TitleBonus.NONE)) {
            return bonus;
        }
        return bonus.withStats(
                bonus.atk() + title.atk(),
                bonus.def() + title.def(),
                bonus.res() + title.res(),
                bonus.hp() + title.hp());
    }
}
