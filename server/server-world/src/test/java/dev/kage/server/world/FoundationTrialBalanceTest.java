package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cân bằng của quãng Lv28–36 — đệ tử ngoại môn vs {@code wasteland_beast} và
 * {@code foundation_warden} (quest 16–20, seed V48, STORY.md §6 arc 2), nhịp ĐÓNG arc 2.
 *
 * <p><b>Vì sao có lớp này.</b> Cùng lý do {@link BloodSandBandBalanceTest}: sáu con số của hai loài
 * trong V48 được <b>CHỌN TỪ</b> phép tính ở đây. Nhưng lớp này còn gác một thứ nữa, và đó là thứ
 * đắt nhất của đợt — <b>THỨ TỰ của chuỗi quest</b>.
 *
 * <p><b>Trần trang bị đã chạm, và nó quyết định thứ tự chuỗi.</b> Playtest 2026-09-16 đo được bộ
 * "Ngoại Môn" (Lv15) ĐÃ mạnh hơn thang quái hiện có — người chơi hạ đầu lĩnh arc 1 với biên 5 nhát.
 * Khi sức người chơi đứng yên mà quái mạnh lên thì biên chỉ MỎNG DẦN, và trận thành một lần tung
 * đồng xu chứ không khó lên. Ô {@link #outerSectGearLosesToTheWastelandBeast()} khoá lại kết luận
 * đó bằng số: với bộ cũ, con Lv29 đầu tiên của quãng là một trận THUA. Vì thế quest MỞ của lát cắt
 * phải TRAO bộ "Trúc Cơ" và trận của chính nó đánh loài CŨ — không phải vì nhịp kể chuyện đẹp hơn,
 * mà vì không còn cách nào khác.
 *
 * <p><b>Máu lúc TỚI NƠI trừ ĐÚNG MỘT nhát phủ đầu.</b> {@code aggro 3} vẫn với tới người đi ngang —
 * đo được hai lần độc lập ở V47 (đoạt mạch @47 đánh 12, đầu lĩnh @93 đánh 20, khớp
 * {@code hit(atk, 50)} tới từng điểm máu). Cửa sổ phủ đầu chỉ biến mất khi
 * {@code aggro <= ATTACK_RANGE_METERS}. Đừng trừ 0 cho loài có {@code aggro > 2}.
 *
 * <p>Nó KHÔNG mô phỏng AI, khoảng cách hay né/bạo kích ngẫu nhiên — chỉ trả lời đúng một câu: với
 * sát thương TRUNG BÌNH mỗi bên, ai hết máu trước?
 */
class FoundationTrialBalanceTest {

    /** Chỉ số nền 10/10/10/10 — không đổi theo cấp, cùng giả định mọi lớp cân bằng khác dùng. */
    private static final int BASE_STAT = 10;

    /** Cấp mở khoá bộ "Trúc Cơ" ({@code item_template.required_level}, V48). */
    private static final int LEVEL_AT_FOUNDATION_SET = 30;

    private static final int OUTER_SECT_SET_ATK = 34;

    private static final int OUTER_SECT_SET_DEF = 30;

    private static final int OUTER_SECT_SET_HP = 70;

    private static final int FOUNDATION_SET_ATK = 52;

    private static final int FOUNDATION_SET_DEF = 45;

    private static final int FOUNDATION_SET_RES = 36;

    private static final int FOUNDATION_SET_HP = 106;

    private static final int OUTER_SECT_SET_RES = 24;

    private static final int BEAST_HP = 280;

    private static final int BEAST_ATK = 44;

    private static final int BEAST_DEF = 32;

    private static final int WARDEN_HP = 300;

    private static final int WARDEN_ATK = 46;

    private static final int WARDEN_DEF = 35;

    /** Đỉnh thang quái TRƯỚC V48 — bậc mà hai loài mới phải vượt (V47). */
    private static final int RINGLEADER_HP = 240;

    private static final int RINGLEADER_ATK = 35;

    private static final int RINGLEADER_DEF = 28;

    /** Giáp giảm sát thương kiểu LMHT/Liên Quân, đúng công thức {@code CombatConstants#resolveAttack}. */
    private static int hit(int attack, int defence) {
        return Math.round(attack * (100f / (100f + defence)));
    }

    private static int hitsToKill(int hitPoints, int damagePerHit) {
        return (int) Math.ceil((double) hitPoints / damagePerHit);
    }

    /**
     * Hệ phái {@code NONE} như các lớp cân bằng khác: hai passive của mọi hệ đều cộng vào
     * SKILL_DMG/bạo kích chứ không vào ATK/DEF, nên con số đòn thường không đổi theo hệ. Nhân vật
     * playtest thật là hệ HOẢ và được {@code +0,05} bạo kích, tức lớp này là cận DƯỚI thận trọng.
     */
    private static CombatConstants.CombatStats disciple() {
        return CombatConstants.deriveCombatStats(new CharacterBaseStats(
                BASE_STAT, BASE_STAT, BASE_STAT, BASE_STAT, Element.NONE, LEVEL_AT_FOUNDATION_SET));
    }

    private static int outerSectAtk() {
        return disciple().atk() + OUTER_SECT_SET_ATK;
    }

    private static int outerSectDef() {
        return disciple().def() + OUTER_SECT_SET_DEF;
    }

    private static int outerSectMaxHp() {
        return CombatConstants.CHARACTER_MAX_HP + OUTER_SECT_SET_HP;
    }

    private static int foundationAtk() {
        return disciple().atk() + FOUNDATION_SET_ATK;
    }

    private static int foundationDef() {
        return disciple().def() + FOUNDATION_SET_DEF;
    }

    private static int foundationMaxHp() {
        return CombatConstants.CHARACTER_MAX_HP + FOUNDATION_SET_HP;
    }

    /** Máu lúc TỚI NƠI = máu tối đa trừ ĐÚNG MỘT nhát phủ đầu — xem javadoc lớp. */
    private static int arrivalHp(int playerMaxHp, int monsterAtk, int playerDef) {
        return playerMaxHp - hit(monsterAtk, playerDef);
    }

    @Test
    @DisplayName("Bộ Trúc Cơ là bậc thang thật trên bộ Ngoại Môn ở CẢ BỐN trục")
    void foundationSetOutranksTheOuterSectSetOnEveryAxis() {
        assertThat(FOUNDATION_SET_ATK).isGreaterThan(OUTER_SECT_SET_ATK);
        assertThat(FOUNDATION_SET_DEF).isGreaterThan(OUTER_SECT_SET_DEF);
        assertThat(FOUNDATION_SET_RES).isGreaterThan(OUTER_SECT_SET_RES);
        assertThat(FOUNDATION_SET_HP).isGreaterThan(OUTER_SECT_SET_HP);

        // Và nó phải đổi được HAI con số mà cân bằng thật sự đọc.
        assertThat(foundationAtk()).isEqualTo(82);
        assertThat(foundationDef()).isEqualTo(65);
        assertThat(foundationMaxHp()).isEqualTo(206);
    }

    /**
     * ⛔ Ô ĐẮT NHẤT CỦA ĐỢT — nó khoá lại LÝ DO quest mở của lát cắt phải trao đồ trước khi đánh.
     *
     * <p>Không có bộ chỉ số nào cho biên ≥2 với bộ Ngoại Môn mà không trùng đúng chỉ số
     * {@code blood_sand_acolyte} (220/34/26) hoặc {@code blood_sand_ringleader} (240/35/28) — tức
     * KHÔNG CÒN bậc thang nào ở trên bộ cũ. Đó là trần đã báo cáo 2026-09-16.
     */
    @Test
    @DisplayName("Mặc bộ NGOẠI MÔN đi đánh yêu thú Lv29 thì THUA — 6 nhát so với 5")
    void outerSectGearLosesToTheWastelandBeast() {
        int playerHitsFor = hitsToKill(BEAST_HP, hit(outerSectAtk(), BEAST_DEF));
        int beastHitsFor =
                hitsToKill(arrivalHp(outerSectMaxHp(), BEAST_ATK, outerSectDef()), hit(BEAST_ATK, outerSectDef()));

        assertThat(playerHitsFor).isEqualTo(6);
        assertThat(beastHitsFor).isEqualTo(5);
        assertThat(beastHitsFor - playerHitsFor).isNegative();
    }

    @Test
    @DisplayName("Mặc bộ Trúc Cơ, thắng yêu thú rìa Hoang Vực — 5 nhát so với 7, biên 2")
    void gearedDiscipleShouldWinAgainstTheWastelandBeast() {
        int playerHitsFor = hitsToKill(BEAST_HP, hit(foundationAtk(), BEAST_DEF));
        int beastHitsFor =
                hitsToKill(arrivalHp(foundationMaxHp(), BEAST_ATK, foundationDef()), hit(BEAST_ATK, foundationDef()));

        assertThat(playerHitsFor).isEqualTo(5);
        assertThat(beastHitsFor - playerHitsFor).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Cỗ khảo hạch Trúc Cơ: biên 2 — nhịp ĐÓNG arc, và là trận cuối của v1")
    void gearedDiscipleShouldWinTheFoundationTrial() {
        int playerHitsFor = hitsToKill(WARDEN_HP, hit(foundationAtk(), WARDEN_DEF));
        int wardenHitsFor =
                hitsToKill(arrivalHp(foundationMaxHp(), WARDEN_ATK, foundationDef()), hit(WARDEN_ATK, foundationDef()));

        assertThat(playerHitsFor).isEqualTo(5);
        assertThat(wardenHitsFor - playerHitsFor).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Cả hai loài là bậc thang thật trên đàn chủ Huyết Sa, và cỗ khảo hạch trên yêu thú")
    void bothSpeciesClimbTheExistingLadder() {
        assertThat(BEAST_HP).isGreaterThan(RINGLEADER_HP);
        assertThat(BEAST_ATK).isGreaterThan(RINGLEADER_ATK);
        assertThat(BEAST_DEF).isGreaterThan(RINGLEADER_DEF);

        assertThat(WARDEN_HP).isGreaterThan(BEAST_HP);
        assertThat(WARDEN_ATK).isGreaterThan(BEAST_ATK);
        assertThat(WARDEN_DEF).isGreaterThan(BEAST_DEF);
    }

    /**
     * Dữ liệu thật thì {@code PostgresMonsterCatalogAdapterIntegrationTest} gác (toạ độ sống trong
     * DB, không trong code); ô này giữ PHÉP TÍNH cạnh phép tính cân bằng, vì trận tay đôi chỉ đúng
     * khi người chơi thật sự đánh MỘT con.
     */
    @Test
    @DisplayName("Ba điểm spawn mới (175/188/201) thoả cả hai luật giãn")
    void theWastelandEdgeIsAQuietPlaceToFightIn() {
        // Hàng xóm phía tây là quỷ đá @161 — vẫn giữ aggro 6 mặc định V25, tức con KHÓ TÍNH nhất.
        float ogreAggroRadiusMeters = 6f;
        float newSpeciesAggroRadiusMeters = 3f;
        float leashRadiusMeters = 10f;
        float meleeAttackRangeMeters = 2f;
        float leashReach = leashRadiusMeters + meleeAttackRangeMeters;

        assertThat(Math.abs(175f - 161f)).isGreaterThan(ogreAggroRadiusMeters + newSpeciesAggroRadiusMeters);
        assertThat(Math.abs(188f - 175f)).isGreaterThan(newSpeciesAggroRadiusMeters + newSpeciesAggroRadiusMeters);
        assertThat(Math.abs(201f - 188f)).isGreaterThan(newSpeciesAggroRadiusMeters + newSpeciesAggroRadiusMeters);

        assertThat(Math.abs(175f - 161f)).isGreaterThan(leashReach);
        assertThat(Math.abs(188f - 175f)).isGreaterThan(leashReach);
        assertThat(Math.abs(201f - 188f)).isGreaterThan(leashReach);
    }
}
