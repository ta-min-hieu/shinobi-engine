package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cân bằng của quest thứ MƯỜI HAI — đệ tử ngoại môn vs {@code raider_chieftain} (quest
 * {@code break_the_raider_band}, seed V45, STORY.md §6 arc 1 nhịp ĐÓNG của quãng Lv10–18).
 *
 * <p><b>Khác mọi lớp cân bằng trước: người chơi đã ĐỔI BỘ ĐỒ.</b> Bốn lớp cũ đều tính trên bộ
 * "Tạp Dịch" vì arc 1 không có bộ nào khác trong tầm với (bộ "Hạ Nhẫn" giá 10.900 vàng). Quest 11
 * trao bộ <b>"Ngoại Môn"</b> ({@code required_level 15}, V45) như phần thưởng — đó chính là "công
 * pháp đầu tiên (Lv15)" mà STORY.md §6 hứa, hiện thực bằng cơ chế ĐÃ CÓ (ADR-036 trang bị có chỉ
 * số thật) thay vì một cơ chế mở khoá chiêu mới.
 *
 * <p>Vì vậy trận này phải được tính trên bộ MỚI. Tính nó trên bộ Tạp Dịch là mô tả một người chơi
 * đã bỏ qua phần thưởng của chính chuỗi quest đang chơi.
 */
class RaiderBandBalanceTest {

    /** Chỉ số nền của nhân vật mới: 10/10/10/10 (V-migration seed, giống mọi nhân vật vừa tạo). */
    private static final int BASE_STAT = 10;

    /** Cấp 15 — mốc {@code required_level} của bộ "Ngoại Môn", và cấp người chơi khi nhận quest 12. */
    private static final int LEVEL_AT_QUEST_TWELVE = 15;

    /**
     * Bộ "Ngoại Môn" SÁU món (V45) — cùng quy ước lũt đồ sáu món của arc 1: ô {@code ACCESSORY_2}
     * để trống (xem javadoc {@link SpiritVeinDuelBalanceTest}).
     *
     * <p>Đặt TRÊN bộ "Hạ Nhẫn" sáu món ({@code atk 26 · def 23 · hp 48}) vì nó mở ở Cấp 15 chứ
     * không phải Cấp 5 — một bộ cấp cao hơn mà yếu hơn thì không ai mặc.
     */
    private static final int OUTER_SECT_SET_ATK = 34;

    private static final int OUTER_SECT_SET_DEF = 30;
    private static final int OUTER_SECT_SET_HP = 70;

    /** Bộ "Tạp Dịch" sáu món — mốc so sánh, để thấy phần thưởng quest 11 đáng giá bao nhiêu. */
    private static final int NOVICE_SET_ATK = 7;

    private static final int NOVICE_SET_DEF = 7;
    private static final int NOVICE_SET_HP = 18;

    /** `monster_template` hàng `raider_chieftain` (seed V45) — nguồn duy nhất của ba số này. */
    private static final int CHIEFTAIN_HP = 200;

    private static final int CHIEFTAIN_ATK = 30;
    private static final int CHIEFTAIN_DEF = 18;

    /** `monster_template` hàng `vein_raider` (seed V45) — đàn em của hắn, bậc thang ngay dưới. */
    private static final int RAIDER_HP = 130;

    private static final int RAIDER_ATK = 18;
    private static final int RAIDER_DEF = 12;

    /** Ba loài đứng dọc đường đông; V45 dời quỷ đá 80 → 106, rồi V47 dời tiếp 106 → 161. */
    private static final int BANDIT_ATK = 22;

    private static final int SCOUT_ATK = 26;

    /** Giáp giảm sát thương kiểu LMHT/Liên Quân, đúng công thức {@code CombatConstants#resolveAttack}. */
    private static int hit(int attack, int defence) {
        return Math.round(attack * (100f / (100f + defence)));
    }

    private static int hitsToKill(int hitPoints, int damagePerHit) {
        return (int) Math.ceil((double) hitPoints / damagePerHit);
    }

    private static CombatConstants.CombatStats disciple() {
        return CombatConstants.deriveCombatStats(new CharacterBaseStats(
                BASE_STAT, BASE_STAT, BASE_STAT, BASE_STAT, Element.NONE, LEVEL_AT_QUEST_TWELVE));
    }

    private static int playerAtk() {
        return disciple().atk() + OUTER_SECT_SET_ATK;
    }

    private static int playerDef() {
        return disciple().def() + OUTER_SECT_SET_DEF;
    }

    private static int playerHp() {
        return CombatConstants.CHARACTER_MAX_HP + OUTER_SECT_SET_HP;
    }

    /**
     * Máu lúc CHẠM MẶT đầu lĩnh ở x=93, tính đúng cách {@link AwakeningTrialBalanceTest} đã dạy.
     *
     * <p>Đường đông sau V45: tán tu @21, tán tu @34, đoạt mạch @47, thám tử @66 — mỗi con một nhát.
     * <b>Quỷ đá KHÔNG còn trên đường</b>: V45 dời nó 80 → 106, cùng lý do V40 đã dời nó 47 → 80 một
     * lần rồi (một mình nó lấy 31 máu mỗi lượt đi ngang, đủ để biến trận cuối thành thua chắc).
     *
     * <p>Sau khi dời, x≈80 thành một khe YÊN TĨNH: cách thám tử @66 14m và cách đầu lĩnh @93 13m,
     * tức ngoài tầm aggro của cả hai. Đó là chỗ người chơi đứng lại hồi máu (ADR-046) trước trận —
     * nhưng thiết kế dưới đây vẫn phải đứng vững kể cả khi họ KHÔNG dừng.
     */
    private static int arrivalHp() {
        int def = playerDef();
        return playerHp() - (2 * hit(BANDIT_ATK, def)) - hit(RAIDER_ATK, def) - hit(SCOUT_ATK, def);
    }

    @Test
    @DisplayName("Mặc bộ Ngoại Môn, tới nơi với 111 máu vẫn THẮNG — 4 nhát so với 6, biên 2")
    void gearedDiscipleShouldWinTheChieftainDuel() {
        int playerHitsFor = hitsToKill(CHIEFTAIN_HP, hit(playerAtk(), CHIEFTAIN_DEF));
        int chieftainHitsFor = hitsToKill(arrivalHp(), hit(CHIEFTAIN_ATK, playerDef()));

        // 170 − 2×15 (tán tu) − 12 (đoạt mạch) − 17 (thám tử) = 111. Giáp 50 của bộ Ngoại Môn cắt
        // sát thương đường đi xuống gần một nửa so với bộ Tạp Dịch — đó là nửa còn lại của phần
        // thưởng quest 11, và là lý do quãng đường KHÔNG còn là thứ quyết định trận này.
        assertThat(arrivalHp()).isEqualTo(111);
        assertThat(playerHitsFor).isEqualTo(4);
        assertThat(chieftainHitsFor).isEqualTo(6);
        assertThat(chieftainHitsFor - playerHitsFor).isEqualTo(2);
    }

    /**
     * Vì sao quest 11 phải trao bộ đồ TRƯỚC trận này — câu trả lời là số, không phải nhịp kể chuyện.
     *
     * <p>Mặc bộ "Tạp Dịch" đi đánh đầu lĩnh là thua, và thua không sát nút. Đây chính là thứ biến
     * phần thưởng quest 11 từ "một dòng chữ" thành "điều kiện cần".
     */
    @Test
    @DisplayName("Mặc bộ Tạp Dịch đi đánh đầu lĩnh thì THUA — nên phần thưởng quest 11 là điều kiện cần")
    void theNoviceSetShouldNotBeEnoughForTheChieftain() {
        int noviceAtk = disciple().atk() + NOVICE_SET_ATK;
        int noviceDef = disciple().def() + NOVICE_SET_DEF;
        int noviceHp = CombatConstants.CHARACTER_MAX_HP + NOVICE_SET_HP;

        int playerHitsFor = hitsToKill(CHIEFTAIN_HP, hit(noviceAtk, CHIEFTAIN_DEF));
        int chieftainHitsFor = hitsToKill(noviceHp, hit(CHIEFTAIN_ATK, noviceDef));

        // Ngay cả trên MÁU ĐẦY (cận trên rộng rãi nhất) người chơi vẫn không thắng nổi.
        assertThat(playerHitsFor).isGreaterThanOrEqualTo(chieftainHitsFor);
    }

    /**
     * Đầu lĩnh cũng mắc ĐÚNG lỗi phủ đầu của đàn em, và V46 sửa cả hai cùng lúc.
     *
     * <p>Playtest 2026-09-13 bác thiết kế của `vein_raider` vì `aggro_radius_meters` 6 gấp ba tầm
     * đánh 2: quái chạy tự do 4m và đánh trước 1-2 nhát. Đầu lĩnh khai y hệt, đánh đau hơn (20/nhát
     * lên giáp 50), nên hai nhát phủ đầu là 40 máu — đủ lật trận. **V46 hạ cả hai xuống aggro 3.**
     *
     * <p>⚠️ Trận này CHƯA đo được bằng `gameProbe`: nó chỉ đúng khi mặc bộ "Ngoại Môn", mà probe có
     * `useItemCode` chứ KHÔNG có đường mặc đồ. Con số dưới đây vẫn là giấy — khác với `vein_raider`
     * đã có số đo thật.
     */
    @Test
    @DisplayName("Phủ đầu 1 nhát (sau V46) vẫn THẮNG; phủ đầu 2 nhát thì không")
    void openingHitsDecideTheChieftainDuelToo() {
        int hitTaken = hit(CHIEFTAIN_ATK, playerDef());
        int playerHitsNeeded = hitsToKill(CHIEFTAIN_HP, hit(playerAtk(), CHIEFTAIN_DEF));

        int afterOneOpener = arrivalHp() - hitTaken;
        int afterTwoOpeners = arrivalHp() - (2 * hitTaken);

        assertThat(hitTaken).isEqualTo(20);
        assertThat(playerHitsNeeded).isEqualTo(4);
        // Phủ đầu 1: vẫn còn nhiều nhát hơn số người chơi cần ⇒ thắng.
        assertThat(hitsToKill(afterOneOpener, hitTaken)).isGreaterThan(playerHitsNeeded);
        // Phủ đầu 2: hết biên ⇒ đúng thứ V46 sinh ra để chặn.
        assertThat(hitsToKill(afterTwoOpeners, hitTaken)).isLessThanOrEqualTo(playerHitsNeeded);
    }

    /** Đầu lĩnh phải là bậc thang thật trên đàn em của chính hắn, không phải bản phóng to đổi tên. */
    @Test
    @DisplayName("Đầu lĩnh dày hơn, đau hơn và dai hơn đàn em đoạt mạch")
    void theChieftainShouldBeAStepAboveHisOwnRaiders() {
        assertThat(CHIEFTAIN_HP).isGreaterThan(RAIDER_HP);
        assertThat(CHIEFTAIN_ATK).isGreaterThan(RAIDER_ATK);
        assertThat(CHIEFTAIN_DEF).isGreaterThan(RAIDER_DEF);
    }

    /**
     * Bộ "Ngoại Môn" phải TRÊN bộ "Hạ Nhẫn" — nếu không thì một bộ mở ở Cấp 15 lại thua bộ mở ở
     * Cấp 5, và người chơi có lý do hợp lệ để bỏ phần thưởng quest 11 trong túi.
     */
    @Test
    @DisplayName("Bộ Ngoại Môn (Cấp 15) mạnh hơn bộ Hạ Nhẫn (Cấp 5) ở cả ba trục")
    void theOuterSectSetShouldOutrankTheGeninSet() {
        // Bộ "Hạ Nhẫn" SÁU món theo `item_template` (trừ dây chuyền, cùng quy ước lũt đồ arc 1).
        int geninSetAtk = 26;
        int geninSetDef = 23;
        int geninSetHp = 48;

        assertThat(OUTER_SECT_SET_ATK).isGreaterThan(geninSetAtk);
        assertThat(OUTER_SECT_SET_DEF).isGreaterThan(geninSetDef);
        assertThat(OUTER_SECT_SET_HP).isGreaterThan(geninSetHp);
    }

    /**
     * Luật hình học cho điểm spawn MỚI của V45 — và cho cú DỜI quỷ đá.
     *
     * <p>Dữ liệu thật thì {@code PostgresMonsterCatalogAdapterIntegrationTest} gác; ô này giữ phép
     * tính cạnh phép tính cân bằng, vì trận tay đôi chỉ đúng khi người chơi thật sự đánh MỘT con.
     */
    @Test
    @DisplayName("Sào huyệt (93) yên tĩnh, và hàng xóm đông nay là tế đồ @120 vẫn thoả luật")
    void theRaiderCampIsAQuietPlaceToFightIn() {
        // V46 hạ `raider_chieftain` xuống aggro 3, nhưng luật hình học phải soi theo hàng xóm ĐAU
        // NHẤT: thám tử @66 và quỷ đá @106 vẫn giữ aggro 6 (mặc định V25). Giữ 6 ở đây là CẬN TRÊN
        // có chủ đích — đổi thành 3 sẽ nới lỏng luật theo con số của loài đã được làm hiền đi.
        float neighbourAggroRadiusMeters = 6f;
        float leashRadiusMeters = 10f;
        float meleeAttackRangeMeters = 2f;
        float combinedReach = neighbourAggroRadiusMeters + neighbourAggroRadiusMeters;
        float leashReach = leashRadiusMeters + meleeAttackRangeMeters;

        // Hàng xóm hai phía của sào huyệt: thám tử @66 và — sau khi V47 dời quỷ đá 106 → 161 —
        // tế đồ Huyết Sa @120. Vẫn soi theo cận trên 6 chứ không theo aggro 3 thật của tế đồ.
        assertThat(Math.abs(93f - 66f)).isGreaterThan(combinedReach);
        assertThat(Math.abs(120f - 93f)).isGreaterThan(combinedReach);
        assertThat(Math.abs(93f - 66f)).isGreaterThan(leashReach);
        assertThat(Math.abs(120f - 93f)).isGreaterThan(leashReach);

        // Khe nghỉ ở x≈80: ngoài tầm aggro của CẢ thám tử lẫn đầu lĩnh, nên hồi máu được.
        // Vẫn soi theo 6 (thám tử) chứ không theo 3 của đầu lĩnh sau V46 — khe chỉ yên tĩnh khi
        // con KHÓ TÍNH nhất cũng không với tới.
        assertThat(Math.abs(80f - 66f)).isGreaterThan(neighbourAggroRadiusMeters);
        assertThat(Math.abs(93f - 80f)).isGreaterThan(neighbourAggroRadiusMeters);
    }

    /**
     * 🔴 <b>VI PHẠM luật biên thật (chủ dự án chốt 2026-10-01, {@link DuelMargin}) — CHƯA chỉnh số,
     * chờ chủ dự án.</b>
     *
     * <p>Tới sào huyệt 111 máu, bộ Ngoại Môn vs đầu lĩnh 200/30/18: ta 4 nhát, nó 6 nhát ⇒ biên giấy
     * 2, biên thật −1. Có bộ chỉ số đạt luật trên bậc thang (vd. hạ {@code atk 30 → 20}), nhưng MỌI
     * bộ như vậy làm {@link #theNoviceSetShouldNotBeEnoughForTheChieftain()} hỏng: bộ Tạp Dịch cũng
     * thắng, tức phần thưởng quest 11 thôi là điều kiện cần. Dừng, báo cáo.
     *
     * <p>Ô này KHOÁ đúng con số vi phạm hiện tại, để bất kỳ ai đổi chỉ số loài này đều phải dừng lại
     * đối chiếu với luật biên thật thay vì lặng lẽ giữ trận ở dưới ngưỡng.
     */
    @Test
    @DisplayName("🔴 Luật biên thật: đầu lĩnh đoạt mạch ĐANG vi phạm — biên thật −1, cần +2 (chờ chủ dự án)")
    void theChieftainDuelViolatesTheRealMarginRule() {
        DuelMargin duel =
                new DuelMargin(playerAtk(), playerDef(), arrivalHp(), CHIEFTAIN_HP, CHIEFTAIN_ATK, CHIEFTAIN_DEF);

        assertThat(duel.paperMargin()).isEqualTo(2);
        assertThat(duel.realMargin()).isEqualTo(-1);
        assertThat(duel.meetsRealMarginRule()).isFalse();
    }
}
