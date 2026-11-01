package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cân bằng của quãng Lv22–27 — đệ tử ngoại môn vs {@code blood_sand_acolyte} và
 * {@code blood_sand_ringleader} (quest 13–15, seed V47, STORY.md §6 arc 2).
 *
 * <p><b>Vì sao có lớp này.</b> Cùng lý do {@link SpiritVeinDuelBalanceTest}: bốn con số của hai loài
 * trong V47 được <b>CHỌN TỪ</b> phép tính ở đây, không phải chọn bằng cảm giác rồi mới đi kiểm.
 *
 * <p><b>Mốc trang bị đổi so với arc 1: bộ "Ngoại Môn" SÁU MÓN, máu 170.</b> Không còn là bộ "Tạp
 * Dịch" 118 máu nữa — quest 11 trao bộ này và playtest 2026-09-13 đo thẳng trên server thật: máu
 * tối đa leo đúng từng bước 118 → 136 → 146 → 152 → 158 → <b>170</b>, mỗi bước một món. Ô
 * {@code ACCESSORY_2} vẫn để trống, đúng lũt đồ mà mọi lớp cân bằng của dự án giả định.
 *
 * <p>Nó KHÔNG mô phỏng AI, khoảng cách hay né/bạo kích ngẫu nhiên — chỉ trả lời đúng một câu: với
 * sát thương TRUNG BÌNH mỗi bên, ai hết máu trước?
 */
class BloodSandBandBalanceTest {

    /** Chỉ số nền: 10/10/10/10 — không đổi theo cấp (kiểm DB 2026-09-13, Cấp 22 vẫn 10/10/10/10). */
    private static final int BASE_STAT = 10;

    /**
     * Cấp 22 — cấp người chơi đứng vào lúc nhận quest 13, đo thật: trọn 12 quest đưa {@code Kagemaru}
     * tới Cấp 22. Cấp KHÔNG vào công thức derive; nó ở đây để ghi rõ giả định.
     */
    private static final int LEVEL_AT_QUEST_THIRTEEN = 22;

    /** Bộ "Ngoại Môn" SÁU món (`outer_sect_*` trừ ô ACCESSORY_2) — seed V45, đo thật 2026-09-13. */
    private static final int OUTER_SECT_SET_ATK = 34;

    private static final int OUTER_SECT_SET_DEF = 30;
    private static final int OUTER_SECT_SET_HP = 70;

    /** `monster_template` hàng `blood_sand_acolyte` (seed V47) — nguồn duy nhất của ba số này. */
    private static final int ACOLYTE_HP = 220;

    private static final int ACOLYTE_ATK = 31; // V58: 34 → 31
    private static final int ACOLYTE_DEF = 26;

    /** `monster_template` hàng `blood_sand_ringleader` (seed V47). */
    private static final int RINGLEADER_HP = 240;

    private static final int RINGLEADER_ATK = 32; // V58: 35 → 32
    private static final int RINGLEADER_DEF = 28;

    /** `monster_template` hàng `raider_chieftain` (seed V45) — bậc thang ngay dưới, nhịp đóng arc 1. */
    private static final int CHIEFTAIN_HP = 200;

    private static final int CHIEFTAIN_ATK = 26; // V58: 30 → 26
    private static final int CHIEFTAIN_DEF = 18;

    /** Giáp giảm sát thương kiểu LMHT/Liên Quân, đúng công thức {@code CombatConstants#resolveAttack}. */
    private static int hit(int attack, int defence) {
        return Math.round(attack * (100f / (100f + defence)));
    }

    private static int hitsToKill(int hitPoints, int damagePerHit) {
        return (int) Math.ceil((double) hitPoints / damagePerHit);
    }

    /**
     * Hệ phái {@code NONE} như các lớp cân bằng khác: hai passive của mọi hệ đều cộng vào
     * SKILL_DMG/bạo kích chứ không vào ATK/DEF, nên con số đòn thường không đổi theo hệ.
     *
     * <p>⚠️ Nhân vật playtest thật là hệ HOẢ, và passive Hoả cộng {@code +0,05} bạo kích — tức người
     * chơi thật crit 6% chứ không phải 1%. Điều đó chỉ làm họ MẠNH hơn phép tính ở đây, nên lớp này
     * là cận DƯỚI thận trọng (đúng như phát hiện 2026-09-13, khi hai nhát 50 trong một trận suýt bị
     * chẩn đoán nhầm thành dị thường).
     */
    private static CombatConstants.CombatStats outerDisciple() {
        return CombatConstants.deriveCombatStats(new CharacterBaseStats(
                BASE_STAT, BASE_STAT, BASE_STAT, BASE_STAT, Element.NONE, LEVEL_AT_QUEST_THIRTEEN));
    }

    private static int playerAtk() {
        return outerDisciple().atk() + OUTER_SECT_SET_ATK;
    }

    private static int playerDef() {
        return outerDisciple().def() + OUTER_SECT_SET_DEF;
    }

    private static int playerMaxHp() {
        return CombatConstants.characterMaxHp(LEVEL_AT_QUEST_THIRTEEN, OUTER_SECT_SET_HP);
    }

    /**
     * Máu lúc TỚI NƠI — quãng đường LÀ một phần của bài toán, nhưng luật ở đây KHÁC arc 1.
     *
     * <p><b>Tính TỪ khe yên tĩnh @80, không từ cổng tông.</b> {@link SpiritVeinDuelBalanceTest} khai
     * ngược lại — *"thiết kế phải đứng vững cả khi họ KHÔNG dừng"* — và luật đó vẫn đúng cho quãng
     * 47m của arc 1. Nhưng quãng này dài 120m, và V45 đã CỐ Ý dời quỷ đá 80 → 106 để tạo khe nghỉ ở
     * x≈80 cho đúng việc này (ADR-046: 8 giây ngoài chiến đấu → 3% máu tối đa mỗi giây).
     *
     * <p>Trừ tuyến tính từ cổng tông cho ra 96 máu, và ở mức đó KHÔNG tồn tại bộ chỉ số nào vừa mạnh
     * hơn {@code raider_chieftain} vừa giữ biên ≥2 — tức lát cắt thua chắc. Chủ dự án chốt luật mới
     * 2026-09-14: quãng ≥100m có khe nghỉ hợp lệ thì tính từ khe.
     *
     * <p><b>ĐO THẬT 2026-09-14, và số đo đã bác bản đầu của chính hàm này.</b> Bản đầu khai rằng
     * hai loài đoạt mạch @47/@93 sau V46 "{@code aggro 3} nên không với tới người đi ngang ở 5 m/s",
     * rồi chỉ trừ một nhát 40 của quỷ đá ⇒ 143. {@code gameProbe} bác cả hai vế:
     *
     * <ul>
     *   <li><b>aggro 3 VẪN với tới, đúng một nhát.</b> Đoạt mạch @47 đánh 12, đầu lĩnh @93 đánh 20 —
     *       hai lần xác nhận độc lập, khớp {@code hit(atk, 50)} tới từng điểm máu. V46 THU HẸP cửa
     *       sổ phủ đầu xuống một nhát chứ KHÔNG xoá nó, đúng như lời V46 đã được sửa lại. Quy ước
     *       này {@link RaiderBandBalanceTest} vốn đã làm đúng: nó trừ cả nhát của
     *       {@code vein_raider} dù con đó cũng aggro 3.
     *   <li><b>Đi thẳng từ cổng tông thì tới nơi còn 91, không phải 143</b> — năm con đánh trúng dọc
     *       đường (15+15+12+17+20). Ở 91 máu tế đồ chỉ cần 4 nhát: trận thua trên giấy, và lượt đo
     *       đó thắng được là nhờ một chí mạng cộng một lần né.
     * </ul>
     *
     * <p><b>Vì sao nay chỉ còn một số hạng.</b> Chủ dự án chốt 2026-09-14 (lượt hai): dời quỷ đá
     * {@code 106 → 161}, ra SAU đàn chủ @148 — lần thứ BA cùng một nước đi, sau V40 ({@code 47→80})
     * và V45 ({@code 80→106}), và cùng một lý do mỗi lần: nó là con đánh đau nhất trục chơi
     * ({@code atk 40}) và nó nằm chắn đường tới nội dung mới. Nên từ khe @80 ra @120 chỉ còn ĐẦU
     * LĨNH @93 với tới ⇒ {@code 170 − 20 = 150}, và 150 là con số ĐO ĐƯỢC ở giai đoạn hai của lượt
     * đo (máu ngay trước nhát đầu của tế đồ).
     */
    private static int arrivalHp() {
        return playerMaxHp() - hit(CHIEFTAIN_ATK, playerDef());
    }

    @Test
    @DisplayName("Tới mạch chết vẫn THẮNG tế đồ — 5 nhát so với 10, biên 5 (V58)")
    void gearedDiscipleShouldWinAgainstAnAcolyte() {
        int playerHitsFor = hitsToKill(ACOLYTE_HP, hit(playerAtk(), ACOLYTE_DEF));
        int acolyteHitsFor = hitsToKill(arrivalHp(), hit(ACOLYTE_ATK, playerDef()));

        assertThat(playerHitsFor).isEqualTo(5);
        assertThat(acolyteHitsFor - playerHitsFor).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Đàn chủ dày hơn, đau hơn và dai hơn tế đồ của chính hắn")
    void theRingleaderShouldBeAStepAboveHisOwnAcolytes() {
        assertThat(RINGLEADER_HP).isGreaterThan(ACOLYTE_HP);
        assertThat(RINGLEADER_ATK).isGreaterThan(ACOLYTE_ATK);
        assertThat(RINGLEADER_DEF).isGreaterThan(ACOLYTE_DEF);
    }

    @Test
    @DisplayName("Cả hai loài là bậc thang thật trên đầu lĩnh đoạt mạch (nhịp đóng arc 1)")
    void bothSpeciesShouldOutrankTheArcOneCloser() {
        assertThat(ACOLYTE_HP).isGreaterThan(CHIEFTAIN_HP);
        assertThat(ACOLYTE_ATK).isGreaterThan(CHIEFTAIN_ATK);
        assertThat(ACOLYTE_DEF).isGreaterThan(CHIEFTAIN_DEF);
    }

    /**
     * Biên 2 với đàn chủ — và vì sao KHÔNG phải 3.
     *
     * <p>{@code AwakeningTrialBalanceTest} chọn biên 3 cho nhịp đóng arc, nên phản xạ đầu là đòi 3 ở
     * đây. Quét toàn bộ không gian cho thấy biên 3 đòi tế đồ xuống {@code hp 200} — tức BẰNG đầu lĩnh
     * arc 1 chứ không hơn, phá luật bậc thang ngay trên. Và đàn chủ phải lấy {@code atk 35} chứ không
     * phải 36: với 36 hắn cần {@code hp ≤ 200} để giữ biên 2, mà hp phải > 220 để trên tế đồ.
     *
     * <p>Đó là hệ quả đo được của việc bộ "Ngoại Môn" mạnh hơn thang quái hiện có: người chơi
     * {@code atk 64 / def 50 / hp 170} đánh đầu lĩnh arc 1 với biên 5. Ghi lại ở đây vì ràng buộc
     * này sẽ còn siết chặt hơn ở Lv28–36, và lúc đó nó là vấn đề THIẾT KẾ chứ không phải con số.
     */
    @Test
    @DisplayName("Đàn chủ: biên 2 — và biên 3 là bất khả ở bậc trang bị này")
    void theRingleaderDuelShouldKeepATwoSwingMargin() {
        int playerHitsFor = hitsToKill(RINGLEADER_HP, hit(playerAtk(), RINGLEADER_DEF));
        int ringleaderHitsFor = hitsToKill(arrivalHp(), hit(RINGLEADER_ATK, playerDef()));

        assertThat(playerHitsFor).isEqualTo(5);
        assertThat(ringleaderHitsFor - playerHitsFor).isGreaterThanOrEqualTo(2);
    }

    /**
     * Luật hình học cho ba điểm spawn MỚI của V47.
     *
     * <p>Dữ liệu thật thì {@code PostgresMonsterCatalogAdapterIntegrationTest} gác; ô này giữ phép
     * tính cạnh phép tính cân bằng, vì trận tay đôi chỉ đúng khi người chơi thật sự đánh MỘT con.
     */
    @Test
    @DisplayName("Ba điểm mạch chết (120/136/152 — V57) thoả cả hai luật giãn")
    void theDeadVeinIsAQuietPlaceToFightIn() {
        // Sau khi V47 dời quỷ đá 106 → 161, hàng xóm hai ĐẦU của vùng mạch chết đổi chỗ cho nhau:
        // phía tây là đầu lĩnh @93 (aggro 3 sau V46), phía đông là chính quỷ đá @161 (aggro 6).
        float ogreAggroRadiusMeters = 6f;
        float bandAggroRadiusMeters = 3f;
        float leashRadiusMeters = 10f;
        float meleeAttackRangeMeters = 2f;
        float leashReach = leashRadiusMeters + meleeAttackRangeMeters;

        assertThat(Math.abs(120f - 93f)).isGreaterThan(bandAggroRadiusMeters + bandAggroRadiusMeters);
        assertThat(Math.abs(136f - 120f)).isGreaterThan(bandAggroRadiusMeters + bandAggroRadiusMeters);
        assertThat(Math.abs(152f - 136f)).isGreaterThan(bandAggroRadiusMeters + bandAggroRadiusMeters);
        assertThat(Math.abs(168f - 152f)).isGreaterThan(ogreAggroRadiusMeters + bandAggroRadiusMeters);

        assertThat(Math.abs(120f - 93f)).isGreaterThan(leashReach);
        assertThat(Math.abs(136f - 120f)).isGreaterThan(leashReach);
        assertThat(Math.abs(152f - 136f)).isGreaterThan(leashReach);
        assertThat(Math.abs(168f - 152f)).isGreaterThan(leashReach);
    }

    /** Nền đất phải phủ hết điểm xa nhất — hằng số client, spawn ở DB, nên hai thứ đi cùng lượt. */
    @Test
    @DisplayName("Nền đất nới 305 → 417 (V113) phủ hết cổ thạch quái @408 và còn biên 9m")
    void theGroundStripShouldCoverTheFurthestSpawn() {
        // Điểm xa nhất đổi lần nữa: V48 đặt cỗ khảo hạch Trúc Cơ ở @201, vượt qua quỷ đá @161.
        // Ô này CỐ Ý soi điểm xa nhất TOÀN trục chứ không soi riêng lát cắt nào — luật "nền đất
        // phải phủ hết spawn" từng bị vi phạm trong im lặng hai lần trước khi có nó.
        float groundMaxX = 417f; // V109 (arc 3) nới 225 → 305; V113 (arc 4) → 417
        float furthestSpawnX = 408f; // V113 — cổ thạch quái hộ mạch, điểm xa nhất mới

        assertThat(groundMaxX).isGreaterThan(furthestSpawnX);
        assertThat(groundMaxX - furthestSpawnX).isEqualTo(9f);
    }

    /**
     * ✅ <b>ĐẠT luật biên thật từ V58 (lô 14, chủ dự án duyệt 2026-10-17)</b> — hạ ATK quái, xem bảng ở
     * đầu file migration. Đoạn dưới là LỊCH SỬ vì sao trước đó không có bộ chỉ số nào đạt.
     *
     * <p>Tới mạch chết 150 máu, bộ Ngoại Môn: tế đồ 220/34/26 và đàn chủ 240/35/28 đều 5 nhát so với
     * 7 ⇒ biên giấy 2, biên thật −1. Đây đúng là trần trang bị {@link
     * #theRingleaderDuelShouldKeepATwoSwingMargin()} đã báo: biên giấy 3 đã bất khả trên bậc thang
     * đầu lĩnh arc 1 &lt; tế đồ &lt; đàn chủ &lt; yêu thú, nói gì biên giấy 5. Quét toàn bộ không
     * gian: 0 lời giải. Cần quyết định THIẾT KẾ (trang bị/bậc thang), không phải con số.
     *
     * <p>Ô này KHOÁ đúng con số vi phạm hiện tại, để bất kỳ ai đổi chỉ số loài này đều phải dừng lại
     * đối chiếu với luật biên thật thay vì lặng lẽ giữ trận ở dưới ngưỡng.
     */
    @Test
    @DisplayName("Luật biên thật: tế đồ, đàn chủ Huyết Sa ĐẠT — biên thật +2 (V58; trước đó +1)")
    void theBloodSandDuelsViolateTheRealMarginRule() {
        DuelMargin acolyte =
                new DuelMargin(playerAtk(), playerDef(), arrivalHp(), ACOLYTE_HP, ACOLYTE_ATK, ACOLYTE_DEF);
        DuelMargin ringleader =
                new DuelMargin(playerAtk(), playerDef(), arrivalHp(), RINGLEADER_HP, RINGLEADER_ATK, RINGLEADER_DEF);

        assertThat(acolyte.paperMargin()).isEqualTo(5);
        assertThat(acolyte.realMargin()).isEqualTo(2);
        assertThat(acolyte.meetsRealMarginRule()).isTrue();
        assertThat(ringleader.paperMargin()).isEqualTo(5);
        assertThat(ringleader.realMargin()).isEqualTo(2);
        assertThat(ringleader.meetsRealMarginRule()).isTrue();
    }
}
