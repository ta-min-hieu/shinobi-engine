package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.achievement.domain.TitleBonusPort;
import dev.kage.server.world.EquipmentBonusSnapshot;

/** ADR-210 — danh hiệu đang đeo cộng phẳng vào chỉ số trang bị; không đeo gì thì giữ nguyên. */
class TitleEffectsTest {

    @Test
    void shouldAddTheWornTitleStats_andLeaveOthersUntouched() {
        EquipmentBonusSnapshot gear = EquipmentBonusSnapshot.NONE.withStats(10, 5, 3, 100);

        EquipmentBonusSnapshot withTitle = TitleEffects.plus(gear, new TitleBonusPort.TitleBonus(4, 0, 4, 20));

        assertThat(withTitle.atk()).isEqualTo(14);
        assertThat(withTitle.def()).isEqualTo(5);
        assertThat(withTitle.res()).isEqualTo(7);
        assertThat(withTitle.hp()).isEqualTo(120);
        assertThat(TitleEffects.plus(gear, TitleBonusPort.TitleBonus.NONE)).isSameAs(gear);
    }
}
