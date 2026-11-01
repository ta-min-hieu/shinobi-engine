package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cân bằng arc 4 "Hạ Sơn" (Kim Đan, Lv55–72, V113, ADR-139) — mười lăm con số của năm loài mới được CHỌN TỪ luật biên
 * thật ({@link DuelMargin}) ở đây, cùng khuôn {@link InnerSectBalanceTest}.
 *
 * <p>Người chơi: nền 10/10/10/10, hệ {@code NONE} (cận dưới thận trọng), đã qua BA kiếp (ADR-126: mỗi kiếp +3 công/
 * thủ/kháng, +30 máu) — không qua kiếp thì không lên được Cấp 55. Không tính pháp bảo, trận pháp, linh thú, tẩy luyện.
 *
 * <p>Thứ tự chuỗi do cổng trang bị quyết: trận MỞ đánh bằng bộ Nội Môn và trao bộ Kim Đan; quật mạch đồ và cổ thạch
 * quái CHẶN bộ Nội Môn, nên quest mở bắt buộc phải đi trước.
 */
class GoldenCoreBalanceTest {

    private static final int BASE_STAT = 10;
    private static final int TRIBULATIONS = 3;
    private static final int TRIBULATION_STAT = 3;
    private static final int TRIBULATION_HP = 30;

    /** Bộ "Nội Môn" — tổng sáu món ở V109. */
    private static final int INNER_SET_ATK = 70;

    private static final int INNER_SET_DEF = 60;
    private static final int INNER_SET_RES = 48;
    private static final int INNER_SET_HP = 150;

    /** Bộ "Kim Đan" — tổng sáu món ở V113. */
    private static final int GOLDEN_SET_ATK = 92;

    private static final int GOLDEN_SET_DEF = 78;
    private static final int GOLDEN_SET_RES = 64;
    private static final int GOLDEN_SET_HP = 200;

    /** {@code monster_template} V113: máu / công / thủ. */
    private static final int[] MARAUDER = {490, 70, 54};

    private static final int[] DIGGER = {540, 78, 58};

    private static final int[] BEAST_WARDEN = {570, 82, 60};

    private static final int[] FRENZIED_BEAST = {600, 84, 62};

    private static final int[] COLOSSUS = {640, 88, 64};

    /** {@code blood_sand_altar_master} V109 — bậc cuối của arc 3. */
    private static final int[] ALTAR_MASTER = {480, 68, 52};

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
                new CharacterBaseStats(BASE_STAT, BASE_STAT, BASE_STAT, BASE_STAT, Element.NONE, 55));
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

    private static DuelMargin golden(int level, int[] monster) {
        return new DuelMargin(
                discipleAtk(GOLDEN_SET_ATK),
                discipleDef(GOLDEN_SET_DEF),
                discipleHp(level, GOLDEN_SET_HP),
                monster[0],
                monster[1],
                monster[2]);
    }

    @Test
    @DisplayName("Người chơi Cấp 55 đã qua ba kiếp: bộ Nội Môn 109/89/448, bộ Kim Đan Cấp 60 131/107/508")
    void playerSnapshotsAreWhatV113WasChosenFrom() {
        assertThat(discipleAtk(INNER_SET_ATK)).isEqualTo(109);
        assertThat(discipleDef(INNER_SET_DEF)).isEqualTo(89);
        assertThat(discipleHp(55, INNER_SET_HP)).isEqualTo(448);
        assertThat(discipleAtk(GOLDEN_SET_ATK)).isEqualTo(131);
        assertThat(discipleDef(GOLDEN_SET_DEF)).isEqualTo(107);
        assertThat(discipleHp(60, GOLDEN_SET_HP)).isEqualTo(508);
    }

    @Test
    @DisplayName("Trận MỞ arc 4 thắng được bằng bộ Nội Môn — biên thật +3")
    void theMarauderIsWinnableWithTheInnerSectSet() {
        assertThat(inner(55, MARAUDER).realMargin()).isEqualTo(3);
    }

    @Test
    @DisplayName("Quật mạch đồ và cổ thạch quái CHẶN bộ Nội Môn — quest mở phải đi trước")
    void laterFightsGateOnTheGoldenCoreSet() {
        assertThat(inner(60, DIGGER).meetsRealMarginRule()).isFalse();
        assertThat(inner(70, COLOSSUS).meetsRealMarginRule()).isFalse();
    }

    @Test
    @DisplayName("Mặc bộ Kim Đan, mọi trận arc 4 đạt luật biên thật (≥2) ở đúng cấp của nó")
    void everyArcFourFightMeetsTheRealMarginRuleInTheGoldenCoreSet() {
        assertThat(golden(60, DIGGER).realMargin()).isEqualTo(4);
        assertThat(golden(64, BEAST_WARDEN).realMargin()).isEqualTo(3);
        assertThat(golden(67, FRENZIED_BEAST).realMargin()).isEqualTo(2);
        assertThat(golden(70, COLOSSUS).realMargin()).isEqualTo(2);
    }

    @Test
    @DisplayName("Bộ Kim Đan là bậc thang thật trên bộ Nội Môn ở cả bốn trục")
    void goldenCoreSetOutranksTheInnerSectSet() {
        assertThat(GOLDEN_SET_ATK).isGreaterThan(INNER_SET_ATK);
        assertThat(GOLDEN_SET_DEF).isGreaterThan(INNER_SET_DEF);
        assertThat(GOLDEN_SET_RES).isGreaterThan(INNER_SET_RES);
        assertThat(GOLDEN_SET_HP).isGreaterThan(INNER_SET_HP);
    }

    @Test
    @DisplayName("Năm loài là bậc thang thật: mỗi loài vượt loài trước ở máu, công VÀ thủ")
    void theFiveSpeciesClimbTheLadder() {
        int[][] ladder = {ALTAR_MASTER, MARAUDER, DIGGER, BEAST_WARDEN, FRENZIED_BEAST, COLOSSUS};
        for (int i = 1; i < ladder.length; i++) {
            for (int axis = 0; axis < 3; axis++) {
                assertThat(ladder[i][axis]).isGreaterThan(ladder[i - 1][axis]);
            }
        }
    }
}
