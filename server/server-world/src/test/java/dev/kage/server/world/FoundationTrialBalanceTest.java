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
 * <p><b>Luật biên thật (2026-10-01, {@link DuelMargin}).</b> Hai ô "biên ≥2 sau một nhát phủ đầu"
 * bên dưới là luật CŨ và vẫn được giữ, nhưng chúng đã xanh trong khi trận thật thua (V48: 1 thắng
 * 2 chết). Luật quyết định nay là hai ô {@code *MeetsTheRealMarginRule} — biên giấy trên máu ĐẦY
 * trừ 2 nhát phủ đầu và 1 lần né, phải ≥2. V50 chỉnh chỉ số hai loài cho đạt luật đó.
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

    /** `monster_template` hàng `wasteland_beast` — V48 seed 280/44/32, V50 chỉnh lại theo luật biên thật. */
    private static final int BEAST_HP = 289;

    private static final int BEAST_ATK = 36;

    private static final int BEAST_DEF = 32;

    /** `monster_template` hàng `foundation_warden` — V48 seed 300/46/35, V50 hạ atk theo luật biên thật. */
    private static final int WARDEN_HP = 300;

    private static final int WARDEN_ATK = 37;

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
        return CombatConstants.characterMaxHp(
                LEVEL_AT_FOUNDATION_SET - 1, OUTER_SECT_SET_HP); // Cấp 29, trước bộ Trúc Cơ
    }

    private static int foundationAtk() {
        return disciple().atk() + FOUNDATION_SET_ATK;
    }

    private static int foundationDef() {
        return disciple().def() + FOUNDATION_SET_DEF;
    }

    private static int foundationMaxHp() {
        return CombatConstants.characterMaxHp(LEVEL_AT_FOUNDATION_SET, FOUNDATION_SET_HP);
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
        assertThat(foundationMaxHp()).isEqualTo(264);
    }

    /**
     * ⛔ Ô ĐẮT NHẤT CỦA ĐỢT — nó khoá lại LÝ DO quest mở của lát cắt phải trao đồ trước khi đánh.
     *
     * <p>Không có bộ chỉ số nào cho biên ≥2 với bộ Ngoại Môn mà không trùng đúng chỉ số
     * {@code blood_sand_acolyte} (220/34/26) hoặc {@code blood_sand_ringleader} (240/35/28) — tức
     * KHÔNG CÒN bậc thang nào ở trên bộ cũ. Đó là trần đã báo cáo 2026-09-16.
     *
     * <p><b>V50 (luật biên thật, 2026-10-01) nới phép so ở đây từ "thua hẳn" thành "hoà là chết".</b>
     * Quét toàn bộ không gian (yêu thú × cỗ khảo hạch, giữ luật bậc thang của
     * {@link #bothSpeciesClimbTheExistingLadder()}) cho thấy KHÔNG có cặp chỉ số nào vừa cho bộ
     * Trúc Cơ biên thật ≥2 ở CẢ HAI trận vừa giữ biên giấy của bộ Ngoại Môn ÂM hẳn — tốt nhất là hoà
     * 7–7. Hoà chung nhịp đánh nghĩa là chết, đúng quy ước {@link OldArchiveDuelBalanceTest} và
     * {@link AwakeningTrialBalanceTest} đã dùng từ đầu. Và dưới chính luật biên thật, bộ Ngoại Môn
     * đứng ở −2: không phải tung đồng xu, mà là thua.
     */
    @Test
    @DisplayName("Mặc bộ NGOẠI MÔN đi đánh yêu thú Lv29 thì KHÔNG ĐỦ — thắng 2 nhát trên giấy, biên thật 0")
    void outerSectGearLosesToTheWastelandBeast() {
        int playerHitsFor = hitsToKill(BEAST_HP, hit(outerSectAtk(), BEAST_DEF));
        int beastHitsFor =
                hitsToKill(arrivalHp(outerSectMaxHp(), BEAST_ATK, outerSectDef()), hit(BEAST_ATK, outerSectDef()));

        assertThat(playerHitsFor).isEqualTo(7);
        assertThat(beastHitsFor).isEqualTo(9);
        // Trước máu theo cấp (65(d), +2/cấp): hoà 7–7, hoà là chết. Nay Cấp 29 có thêm
        // 56 máu nên thắng 2 nhát TRÊN GIẤY —
        // luật biên thật bên dưới mới là trọng tài, và nó vẫn bác: bộ Ngoại Môn KHÔNG đủ.
        assertThat(beastHitsFor - playerHitsFor).isEqualTo(2);

        DuelMargin outerSect =
                new DuelMargin(outerSectAtk(), outerSectDef(), outerSectMaxHp(), BEAST_HP, BEAST_ATK, BEAST_DEF);
        assertThat(outerSect.realMargin()).isEqualTo(0);
        assertThat(outerSect.meetsRealMarginRule()).isFalse();
    }

    @Test
    @DisplayName("Mặc bộ Trúc Cơ, thắng yêu thú rìa Hoang Vực — 5 nhát so với 11 sau một nhát phủ đầu")
    void gearedDiscipleShouldWinAgainstTheWastelandBeast() {
        int playerHitsFor = hitsToKill(BEAST_HP, hit(foundationAtk(), BEAST_DEF));
        int beastHitsFor =
                hitsToKill(arrivalHp(foundationMaxHp(), BEAST_ATK, foundationDef()), hit(BEAST_ATK, foundationDef()));

        assertThat(playerHitsFor).isEqualTo(5);
        assertThat(beastHitsFor).isEqualTo(11);
        assertThat(beastHitsFor - playerHitsFor).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Cỗ khảo hạch Trúc Cơ: 5 nhát so với 11 sau một nhát phủ đầu — nhịp ĐÓNG arc, trận cuối v1")
    void gearedDiscipleShouldWinTheFoundationTrial() {
        int playerHitsFor = hitsToKill(WARDEN_HP, hit(foundationAtk(), WARDEN_DEF));
        int wardenHitsFor =
                hitsToKill(arrivalHp(foundationMaxHp(), WARDEN_ATK, foundationDef()), hit(WARDEN_ATK, foundationDef()));

        assertThat(playerHitsFor).isEqualTo(5);
        assertThat(wardenHitsFor).isEqualTo(11);
        assertThat(wardenHitsFor - playerHitsFor).isGreaterThanOrEqualTo(2);
    }

    /**
     * Luật biên thật (chủ dự án chốt 2026-10-01, {@link DuelMargin}) cho yêu thú — số đo đã bác
     * bản V48.
     *
     * <p>V48 (280/44/32): ta 5 nhát, nó 8 nhát từ máu đầy ⇒ biên giấy +3, biên thật 0. Ba trận 1v1
     * thật từ máu đầy (gameProbe 2026-09-17, V49): <b>1 thắng, 2 chết</b>. V50 hạ {@code atk 44 → 36}
     * (sát thương mỗi nhát 27 → 22, quái cần 10 nhát) và nâng {@code max_hp 280 → 289} — phần máu
     * thêm chỉ để giữ {@link #outerSectGearLosesToTheWastelandBeast()}: dưới 289 thì bộ Ngoại Môn
     * cần 6 nhát và lại THẮNG.
     */
    @Test
    @DisplayName("Luật biên thật: yêu thú Hoang Vực — biên giấy 7, biên thật +4 (số V48 nay là +2)")
    void theWastelandBeastMeetsTheRealMarginRule() {
        DuelMargin beast =
                new DuelMargin(foundationAtk(), foundationDef(), foundationMaxHp(), BEAST_HP, BEAST_ATK, BEAST_DEF);
        DuelMargin beforeV50 = new DuelMargin(foundationAtk(), foundationDef(), foundationMaxHp(), 280, 44, BEAST_DEF);

        assertThat(beforeV50.paperMargin()).isEqualTo(5);
        assertThat(beforeV50.realMargin()).isEqualTo(2);
        // Với máu theo cấp (65(d), +2/cấp) thì cả số V48 cũng đã đạt luật
        // — V50 vẫn giữ biên rộng hơn.
        assertThat(beforeV50.meetsRealMarginRule()).isTrue();

        assertThat(beast.playerHitsToKill()).isEqualTo(5);
        assertThat(beast.monsterHitsToKill()).isEqualTo(12);
        assertThat(beast.realMargin()).isGreaterThanOrEqualTo(DuelMargin.REQUIRED_REAL_MARGIN);
    }

    /**
     * Luật biên thật cho cỗ khảo hạch Trúc Cơ — trận ĐÓNG arc 2, nội dung cuối của v1.
     *
     * <p>V48 (300/46/35): 5 nhát so với 8 ⇒ biên thật 0, mắc đúng bệnh của yêu thú. V50 hạ
     * {@code atk 46 → 37}. 37 là trần, không phải con số tiện tay: 38 cho 23 sát thương/nhát ⇒ quái
     * chỉ cần 9 nhát ⇒ người chơi phải hạ nó trong 4 nhát ⇒ {@code max_hp ≤ 244}, tức THẤP hơn yêu
     * thú — phá luật bậc thang. Còn atk phải &gt; 36 của yêu thú cũng vì luật đó.
     */
    @Test
    @DisplayName("Luật biên thật: cỗ khảo hạch Trúc Cơ — biên giấy 7, biên thật +4 (số V48 nay là +2)")
    void theFoundationWardenMeetsTheRealMarginRule() {
        DuelMargin warden =
                new DuelMargin(foundationAtk(), foundationDef(), foundationMaxHp(), WARDEN_HP, WARDEN_ATK, WARDEN_DEF);
        DuelMargin beforeV50 =
                new DuelMargin(foundationAtk(), foundationDef(), foundationMaxHp(), WARDEN_HP, 46, WARDEN_DEF);

        assertThat(beforeV50.realMargin()).isEqualTo(2);

        assertThat(warden.playerHitsToKill()).isEqualTo(5);
        assertThat(warden.monsterHitsToKill()).isEqualTo(12);
        assertThat(warden.realMargin()).isGreaterThanOrEqualTo(DuelMargin.REQUIRED_REAL_MARGIN);
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
