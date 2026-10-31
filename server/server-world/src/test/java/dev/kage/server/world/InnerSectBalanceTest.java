package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cân bằng arc 3 "Nội Môn" (Lv37–54, V109, ADR-130) — mười hai con số của bốn loài mới được CHỌN TỪ luật biên thật
 * ({@link DuelMargin}) ở đây, cùng khuôn {@link FoundationTrialBalanceTest}.
 *
 * <p>Người chơi: nền 10/10/10/10, hệ {@code NONE} (cận dưới thận trọng), đã qua HAI kiếp (ADR-126: mỗi kiếp +3 công/
 * thủ/kháng, +30 máu) — không qua kiếp thì không lên được Cấp 37. Không tính pháp bảo, trận pháp, linh thú, tẩy luyện:
 * những thứ đó là phần thưởng thêm, không phải điều kiện của chuỗi chính tuyến.
 *
 * <p>Thứ tự chuỗi do cổng trang bị quyết, như V48: trận MỞ đánh bằng bộ Trúc Cơ và trao bộ Nội Môn; tế đồ trà trộn và
 * đàn chủ CHẶN bộ Trúc Cơ, nên quest mở bắt buộc phải đi trước.
 */
class InnerSectBalanceTest {

    private static final int BASE_STAT = 10;
    private static final int TRIBULATIONS = 2;
    private static final int TRIBULATION_STAT = 3;
    private static final int TRIBULATION_HP = 30;

    private static final int FOUNDATION_SET_ATK = 52;
    private static final int FOUNDATION_SET_DEF = 45;
    private static final int FOUNDATION_SET_HP = 106;

    /** Bộ "Nội Môn" — tổng sáu món ở V109. */
    private static final int INNER_SET_ATK = 70;

    private static final int INNER_SET_DEF = 60;
    private static final int INNER_SET_RES = 48;
    private static final int INNER_SET_HP = 150;

    private static int discipleAtk(int setAtk) {
        return disciple().atk() + setAtk + TRIBULATIONS * TRIBULATION_STAT;
    }

    private static int discipleDef(int setDef) {
        return disciple().def() + setDef + TRIBULATIONS * TRIBULATION_STAT;
    }

    private static int discipleHp(int level, int setHp) {
        return CombatConstants.characterMaxHp(level, setHp + TRIBULATIONS * TRIBULATION_HP);
    }

    private static CombatConstants.CombatStats disciple() {
        return CombatConstants.deriveCombatStats(
                new CharacterBaseStats(BASE_STAT, BASE_STAT, BASE_STAT, BASE_STAT, Element.NONE, 37));
    }

    private static DuelMargin foundation(int level, int[] monster) {
        return new DuelMargin(
                discipleAtk(FOUNDATION_SET_ATK),
                discipleDef(FOUNDATION_SET_DEF),
                discipleHp(level, FOUNDATION_SET_HP),
                monster[0],
                monster[1],
                monster[2]);
    }

    private static DuelMargin inner(int level, int[] monster) {
        return new DuelMargin(
                discipleAtk(INNER_SET_ATK),
                discipleDef(INNER_SET_DEF),
                discipleHp(level, INNER_SET_HP),
                monster[0],
                monster[1],
                monster[2]);
    }

    /** {@code monster_template} V109: máu / công / thủ. */
    private static final int[] TRIAL = {360, 52, 40};

    private static final int[] INFILTRATOR = {400, 60, 46};

    private static final int[] BACH_KINH_CHI = {450, 66, 50};

    private static final int[] ALTAR_MASTER = {480, 68, 52};

    private static final int[] FOUNDATION_WARDEN = {300, 37, 35};

    @Test
    @DisplayName("Người chơi Cấp 37 đã qua hai kiếp: bộ Trúc Cơ 88/71/338, bộ Nội Môn Cấp 42 106/86/392")
    void playerSnapshotsAreWhatV109WasChosenFrom() {
        assertThat(discipleAtk(FOUNDATION_SET_ATK)).isEqualTo(88);
        assertThat(discipleDef(FOUNDATION_SET_DEF)).isEqualTo(71);
        assertThat(discipleHp(37, FOUNDATION_SET_HP)).isEqualTo(338);
        assertThat(discipleAtk(INNER_SET_ATK)).isEqualTo(106);
        assertThat(discipleDef(INNER_SET_DEF)).isEqualTo(86);
        assertThat(discipleHp(42, INNER_SET_HP)).isEqualTo(392);
    }

    @Test
    @DisplayName("Trận MỞ arc 3 thắng được bằng bộ Trúc Cơ — biên thật +3")
    void theInnerTrialIsWinnableWithTheFoundationSet() {
        assertThat(foundation(37, TRIAL).realMargin()).isEqualTo(3);
    }

    @Test
    @DisplayName("Tế đồ trà trộn và đàn chủ CHẶN bộ Trúc Cơ — quest mở phải đi trước")
    void laterFightsGateOnTheInnerSectSet() {
        assertThat(foundation(42, INFILTRATOR).meetsRealMarginRule()).isFalse();
        assertThat(foundation(52, ALTAR_MASTER).meetsRealMarginRule()).isFalse();
    }

    @Test
    @DisplayName("Mặc bộ Nội Môn, mọi trận arc 3 đạt luật biên thật (≥2) ở đúng cấp của nó")
    void everyArcThreeFightMeetsTheRealMarginRuleInTheInnerSet() {
        assertThat(inner(42, INFILTRATOR).realMargin()).isEqualTo(4);
        assertThat(inner(46, BACH_KINH_CHI).realMargin()).isEqualTo(2);
        assertThat(inner(52, ALTAR_MASTER).realMargin()).isEqualTo(2);
    }

    @Test
    @DisplayName("Bộ Nội Môn là bậc thang thật trên bộ Trúc Cơ ở cả bốn trục")
    void innerSectSetOutranksTheFoundationSet() {
        assertThat(INNER_SET_ATK).isGreaterThan(FOUNDATION_SET_ATK);
        assertThat(INNER_SET_DEF).isGreaterThan(FOUNDATION_SET_DEF);
        assertThat(INNER_SET_RES).isGreaterThan(36);
        assertThat(INNER_SET_HP).isGreaterThan(FOUNDATION_SET_HP);
    }

    @Test
    @DisplayName("Bốn loài là bậc thang thật: mỗi loài vượt loài trước ở máu, công VÀ thủ")
    void theFourSpeciesClimbTheLadder() {
        int[][] ladder = {FOUNDATION_WARDEN, TRIAL, INFILTRATOR, BACH_KINH_CHI, ALTAR_MASTER};
        for (int i = 1; i < ladder.length; i++) {
            for (int axis = 0; axis < 3; axis++) {
                assertThat(ladder[i][axis]).isGreaterThan(ladder[i - 1][axis]);
            }
        }
    }
}
