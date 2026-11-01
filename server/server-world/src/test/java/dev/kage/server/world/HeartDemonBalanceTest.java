package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cân bằng tâm ma (ADR-146, V118) — bốn bậc {@code heart_demon_1..4} chọn TỪ luật biên thật ({@link DuelMargin}), cùng
 * khuôn {@link GoldenCoreBalanceTest}.
 *
 * <p>Người độ kiếp đứng ở trần cấp của kiếp (36/54/72/90), đã qua {@code bậc} kiếp, nền 10/10/10/10, hệ {@code NONE},
 * mặc bộ cao nhất mặc được ở cấp đó (Cấp 36: bộ Trúc Cơ — bộ Nội Môn đòi Cấp 37; Cấp 90: bộ Kim Đan, chưa có bộ arc 5).
 * Máu lúc chạm mặt = máu tối đa trừ HAI đợt sét 20% (đợt thứ hai đi ngay trước khi tâm ma hiện), KHÔNG tính đan dược —
 * cận dưới thận trọng.
 */
class HeartDemonBalanceTest {

    private static final int BASE_STAT = 10;
    private static final int TRIBULATION_STAT = 3;
    private static final int TRIBULATION_HP = 30;
    private static final int STRIKE_PERCENT = 20;
    private static final int STRIKES_BEFORE_DEMON = 2;

    /** Bộ trang bị: công / thủ / máu (tổng sáu món). */
    private static final int[] FOUNDATION_SET = {52, 45, 106};

    private static final int[] INNER_SET = {70, 60, 150};

    private static final int[] GOLDEN_SET = {92, 78, 200};

    /** {@code monster_template} V118: máu / công / thủ. */
    private static final int[] DEMON_1 = {240, 34, 38};

    private static final int[] DEMON_2 = {300, 46, 50};

    private static final int[] DEMON_3 = {380, 64, 64};

    private static final int[] DEMON_4 = {420, 70, 72};

    private static DuelMargin duel(int passed, int level, int[] set, int[] demon) {
        CombatConstants.CombatStats base = CombatConstants.deriveCombatStats(
                new CharacterBaseStats(BASE_STAT, BASE_STAT, BASE_STAT, BASE_STAT, Element.NONE, level));
        int maxHp = CombatConstants.characterMaxHp(level, set[2] + passed * TRIBULATION_HP);
        int strike = maxHp * STRIKE_PERCENT / 100;
        return new DuelMargin(
                base.atk() + set[0] + passed * TRIBULATION_STAT,
                base.def() + set[1] + passed * TRIBULATION_STAT,
                maxHp - STRIKES_BEFORE_DEMON * strike,
                demon[0],
                demon[1],
                demon[2]);
    }

    @Test
    @DisplayName("Máu lúc tâm ma hiện: 184 / 250 / 320 / 360 (sáu phần mười máu tối đa)")
    void healthWhenTheDemonAppears() {
        assertThat(duel(1, 36, FOUNDATION_SET, DEMON_1).playerHp()).isEqualTo(184);
        assertThat(duel(2, 54, INNER_SET, DEMON_2).playerHp()).isEqualTo(250);
        assertThat(duel(3, 72, GOLDEN_SET, DEMON_3).playerHp()).isEqualTo(320);
        assertThat(duel(4, 90, GOLDEN_SET, DEMON_4).playerHp()).isEqualTo(360);
    }

    @Test
    @DisplayName("Cả bốn bậc tâm ma đạt luật biên thật (≥2) từ máu sau hai đợt sét, không đan dược")
    void everyHeartDemonMeetsTheRealMarginRule() {
        assertThat(duel(1, 36, FOUNDATION_SET, DEMON_1).realMargin()).isEqualTo(3);
        assertThat(duel(2, 54, INNER_SET, DEMON_2).realMargin()).isEqualTo(2);
        assertThat(duel(3, 72, GOLDEN_SET, DEMON_3).realMargin()).isEqualTo(3);
        assertThat(duel(4, 90, GOLDEN_SET, DEMON_4).realMargin()).isEqualTo(2);
    }

    @Test
    @DisplayName("Bốn bậc là bậc thang thật: mỗi bậc vượt bậc trước ở máu, công VÀ thủ")
    void theFourDemonsClimbTheLadder() {
        int[][] ladder = {DEMON_1, DEMON_2, DEMON_3, DEMON_4};
        for (int i = 1; i < ladder.length; i++) {
            for (int axis = 0; axis < 3; axis++) {
                assertThat(ladder[i][axis]).isGreaterThan(ladder[i - 1][axis]);
            }
        }
    }
}
