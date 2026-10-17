package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cân bằng của quest thứ MƯỜI — đệ tử ngoại môn vs {@code vein_raider} (quest
 * {@code spirit_vein_raiders}, seed V45, STORY.md §6 arc 1 quãng Lv10–18).
 *
 * <p><b>Vì sao có lớp này.</b> Cùng lý do {@link OldArchiveDuelBalanceTest}: chỉ số của loài mới
 * được <b>CHỌN TỪ</b> phép tính ở đây, không phải chọn bằng cảm giác rồi mới đi kiểm. Ba con số của
 * {@code vein_raider} trong V45 đến từ đúng ô test này.
 *
 * <p><b>Mốc trang bị là bộ "Tạp Dịch" SÁU MÓN, máu 118.</b> Không phải bảy món. Lũt đồ giả định của
 * arc 1 bỏ trống ô {@code ACCESSORY_2} — kiểm bằng DB: nhân vật playtest (`Kagemaru`) mặc đúng sáu
 * món, cộng ra {@code atk 7 · def 7 · hp 18}, và vết đo playtest V44 ghi {@code máu 118 -> 94}.
 * Cộng cả bảy món trong {@code item_template} ra {@code 8/7/22} → 122, nhưng đó là TỔNG CATALOG
 * cửa hàng, không phải một lũt đồ ai đang mặc. Hai thứ khác nhau, và nhầm chúng là cách tạo ra một
 * "lỗi" không tồn tại.
 *
 * <p>Nó KHÔNG mô phỏng AI, khoảng cách hay né/bạo kích ngẫu nhiên — chỉ trả lời đúng một câu: với
 * sát thương TRUNG BÌNH mỗi bên, ai hết máu trước?
 */
class SpiritVeinDuelBalanceTest {

    /** Chỉ số nền của nhân vật mới: 10/10/10/10 (V-migration seed, giống mọi nhân vật vừa tạo). */
    private static final int BASE_STAT = 10;

    /**
     * Cấp 10 — cấp người chơi đứng vào lúc nhận quest 10: trọn chuỗi arc 1 cũ về đích ở 4.585 EXP,
     * tức đúng Cấp 10 ({@code QuestIntegrationTest.shouldLandExactlyAtLevelTen_afterTheWholeArcOneChain}).
     * Cấp KHÔNG vào công thức derive; nó ở đây để ghi rõ giả định.
     */
    private static final int LEVEL_AT_QUEST_TEN = 10;

    /** Bộ "Tạp Dịch" SÁU món (`novice_*` trừ dây chuyền, + `wooden_sword`) — xem javadoc lớp. */
    private static final int NOVICE_SET_ATK = 7;

    private static final int NOVICE_SET_DEF = 7;
    private static final int NOVICE_SET_HP = 18;

    /** `monster_template` hàng `vein_raider` (seed V45) — nguồn duy nhất của ba số này. */
    private static final int RAIDER_HP = 130;

    private static final int RAIDER_ATK = 15; // V58: 18 → 15 (luật biên thật)
    private static final int RAIDER_DEF = 12;

    /** `monster_template` hàng `wandering_bandit` (seed V23) — loài đứng dọc đường tới linh mạch. */
    private static final int BANDIT_ATK = 22;

    /** `monster_template` hàng `trial_construct` (seed V33) — loài của quest 4, bậc thang ngay dưới. */
    private static final int CONSTRUCT_HP = 130;

    private static final int CONSTRUCT_ATK = 12; // V58: 15 → 12
    private static final int CONSTRUCT_DEF = 10;

    /** Giáp giảm sát thương kiểu LMHT/Liên Quân, đúng công thức {@code CombatConstants#resolveAttack}. */
    private static int hit(int attack, int defence) {
        return Math.round(attack * (100f / (100f + defence)));
    }

    private static int hitsToKill(int hitPoints, int damagePerHit) {
        return (int) Math.ceil((double) hitPoints / damagePerHit);
    }

    /**
     * Hệ phái {@code NONE} như {@link OldArchiveDuelBalanceTest}: linh căn thức tỉnh ở Lv10 nên
     * người chơi VỪA MỚI có quyền chọn hệ lúc nhận quest này, và hai passive của mọi hệ đều cộng
     * vào SKILL_DMG/bạo kích chứ không vào ATK/DEF — con số đòn thường không đổi theo hệ.
     */
    private static CombatConstants.CombatStats outerDisciple() {
        return CombatConstants.deriveCombatStats(
                new CharacterBaseStats(BASE_STAT, BASE_STAT, BASE_STAT, BASE_STAT, Element.NONE, LEVEL_AT_QUEST_TEN));
    }

    /**
     * Máu lúc CHẠM MẶT, tính như {@link AwakeningTrialBalanceTest} đã dạy: quãng đường LÀ một phần
     * của bài toán, không phải chi tiết bỏ qua được.
     *
     * <p>Đi từ mép vùng an toàn (x=5) sang linh mạch (x=47) lách qua hai tán tu ở 21 và 34; mỗi con
     * kịp ra đúng MỘT nhát (aggro 6m, đuổi 3 m/s trong khi người chơi đi 5 m/s — cùng phép tính
     * V33). Hai cọc gỗ ở 8/14 có {@code aggro_radius_meters = 0} nên không tính.
     *
     * <p>⚠️ Hồi máu ngoài chiến đấu (ADR-046) KHÔNG cứu được quãng này: {@code REGEN_DELAY_SECONDS}
     * là 8 giây, mà hai điểm spawn cách nhau 13m ⇒ ~2,6 giây ở tốc độ 5 m/s. Đồng hồ chưa kịp chạy
     * thì đã ăn nhát tiếp theo. Người chơi CÓ THỂ dừng lại hồi (khe 34→47 rộng 13m, đứng giữa là
     * ngoài tầm aggro của cả hai), nhưng thiết kế phải đứng vững cả khi họ KHÔNG dừng.
     */
    private static int arrivalHp() {
        int playerDef = outerDisciple().def() + NOVICE_SET_DEF;
        return CombatConstants.characterMaxHp(LEVEL_AT_QUEST_TEN, NOVICE_SET_HP) - (2 * hit(BANDIT_ATK, playerDef));
    }

    /**
     * Số nhát quái kịp ra TRƯỚC khi người chơi vung nhát đầu tiên.
     *
     * <p>⚠️ Đây là số hạng mà bản đầu của lớp này BỎ SÓT, và playtest 2026-09-13 bác thiết kế vì
     * đúng chỗ đó. Cùng một trận chạy hai lượt bằng {@code gameProbe}: phủ đầu **1 nhát** ⇒ thắng
     * còn 28 máu; phủ đầu **2 nhát** ⇒ CHẾT, quái còn 31. Đường đi, sát thương, mọi thứ khác giống
     * hệt nhau.
     *
     * <p>Nguyên nhân là hình học chứ không phải chỉ số: {@code aggro_radius_meters} 6 gấp ba
     * {@code ATTACK_RANGE_METERS} 2, nên quái chạy tự do 4m (≈1,3 giây) và đánh trong lúc người chơi
     * còn đang đi tới. **V46 hạ aggro xuống 3**, nên quãng chạy tự do chỉ còn 1m.
     *
     * <p>⚠️ Hằng số này là **CẬN TRÊN thận trọng, không phải số cố định**. Đo lại bằng
     * {@code gameProbe} sau khi V46 chạy thật (12 lượt hợp lệ, 2026-09-13): **0 nhát ở 3 lượt,
     * 1 nhát ở 9 lượt, 2 nhát ở 0 lượt**. V46 xoá hẳn trường hợp 2 nhát — thứ biến trận đấu
     * thành tung đồng xu — chứ không làm mọi lượt đều đúng 1 nhát.
     *
     * <p>Đây chính là bài học *"máu lúc TỚI NƠI ≠ máu lúc BẮT ĐẦU ĐÁNH"* của quest 8, lần này ở
     * dạng một SỐ HẠNG trong công thức thay vì một câu trong tài liệu.
     */
    private static final int OPENING_HITS_AFTER_V46 = 1;

    /** Máu lúc VUNG NHÁT ĐẦU TIÊN — khác máu lúc tới nơi đúng bằng số nhát phủ đầu. */
    private static int hpAtFirstSwing(int openingHits) {
        int playerDef = outerDisciple().def() + NOVICE_SET_DEF;
        return arrivalHp() - (openingHits * hit(RAIDER_ATK, playerDef));
    }

    @Test
    @DisplayName("Tới linh mạch với 102 máu vẫn THẮNG — 4 nhát so với 9, biên 5 nhát (V58)")
    void gearedDiscipleShouldWinTheSpiritVeinDuel() {
        CombatConstants.CombatStats player = outerDisciple();

        int playerAtk = player.atk() + NOVICE_SET_ATK;
        int playerDef = player.def() + NOVICE_SET_DEF;

        int playerHitsFor = hitsToKill(RAIDER_HP, hit(playerAtk, RAIDER_DEF));
        int raiderHitsFor = hitsToKill(arrivalHp(), hit(RAIDER_ATK, playerDef));

        // Hai bên chung nhịp đánh (ATTACK_INTERVAL_SECONDS = 1 đòn/giây), nên "ít nhát hơn" cũng
        // chính là "chết sau" — không cần mô phỏng thời gian.
        assertThat(arrivalHp()).isEqualTo(102);
        assertThat(playerHitsFor).isEqualTo(4);
        assertThat(raiderHitsFor).isEqualTo(9);
        assertThat(raiderHitsFor - playerHitsFor).isEqualTo(5);
    }

    /**
     * Biên 2 nhát là CỐ Ý và nó khớp quest 3, không khớp quest 4.
     *
     * <p>ROADMAP mục 64: người chơi trúng 91% ({@code hitPercent = (90 + agi × 0,1)/100}), nên biên
     * 1 nhát là một lần tung đồng xu. Quest 4 giữ biên 3 vì nó là nhịp ĐÓNG của arc. Quest 10 mở
     * một quãng mới nên nó quay về biên 2 của quest 3: thắng chắc nếu đánh sạch, thua nếu hụt ba
     * nhát liên tiếp.
     */
    @Test
    @DisplayName("Biên 2 nhát — chặt hơn nhịp đóng arc (biên 3), rộng hơn một lần tung đồng xu")
    void theDuelShouldNotBeACoinFlip() {
        CombatConstants.CombatStats player = outerDisciple();
        int playerAtk = player.atk() + NOVICE_SET_ATK;
        int playerDef = player.def() + NOVICE_SET_DEF;

        int margin =
                hitsToKill(arrivalHp(), hit(RAIDER_ATK, playerDef)) - hitsToKill(RAIDER_HP, hit(playerAtk, RAIDER_DEF));

        assertThat(margin).isGreaterThanOrEqualTo(2);
    }

    /**
     * Tán tu đoạt mạch là bậc thang TRÊN cỗ máy khảo hạch — và bậc thang đi theo hướng NGƯỢC với
     * quest 4.
     *
     * <p>{@code trial_construct} là "máu cao, đòn nhẹ" (bài thi trụ lâu). Con này cùng máu nhưng
     * đánh đau hơn và có giáp hơn: nó không đo sức bền, nó là một trận đánh thật với một kẻ đang
     * GIỮ thứ nó cướp được.
     */
    @Test
    @DisplayName("Đau hơn và dày hơn cỗ khảo hạch, không phải bản đổi tên")
    void theRaiderShouldBeAStepAboveTheTrialConstruct() {
        assertThat(RAIDER_ATK).isGreaterThan(CONSTRUCT_ATK);
        assertThat(RAIDER_DEF).isGreaterThan(CONSTRUCT_DEF);
        assertThat(RAIDER_HP).isEqualTo(CONSTRUCT_HP);

        CombatConstants.CombatStats player = outerDisciple();
        int playerAtk = player.atk() + NOVICE_SET_ATK;
        // Giáp dày hơn ⇒ cần nhiều nhát hơn cho cùng lượng máu.
        assertThat(hitsToKill(RAIDER_HP, hit(playerAtk, RAIDER_DEF)))
                .isGreaterThanOrEqualTo(hitsToKill(CONSTRUCT_HP, hit(playerAtk, CONSTRUCT_DEF)));
    }

    /**
     * Với phủ đầu 1 nhát (mức V46 cho phép) người chơi VẪN thắng — đây là ô gác cho cú hạ aggro.
     *
     * <p>Số đo thật của lượt thắng: quái ra 1 nhát phủ đầu rồi hai bên đổi 4-4, người chơi về đích
     * còn 28/118.
     */
    @Test
    @DisplayName("Phủ đầu 1 nhát (sau V46) vẫn THẮNG — biên còn lại ≥ 1")
    void oneOpeningHitStillLeavesTheDuelWinnable() {
        CombatConstants.CombatStats player = outerDisciple();
        int playerAtk = player.atk() + NOVICE_SET_ATK;
        int playerDef = player.def() + NOVICE_SET_DEF;

        int playerHitsNeeded = hitsToKill(RAIDER_HP, hit(playerAtk, RAIDER_DEF));
        int raiderHitsLeft = hitsToKill(hpAtFirstSwing(OPENING_HITS_AFTER_V46), hit(RAIDER_ATK, playerDef));

        assertThat(hpAtFirstSwing(OPENING_HITS_AFTER_V46)).isEqualTo(90);
        assertThat(playerHitsNeeded).isEqualTo(4);
        assertThat(raiderHitsLeft).isGreaterThan(playerHitsNeeded);
    }

    /**
     * Và với phủ đầu 2 nhát thì THUA — ô này giữ lại phép tính của cái đã xảy ra thật, để không ai
     * nâng `aggro_radius_meters` lên lại mà không thấy giá của nó.
     *
     * <p>Số đo thật của lượt thua (gameProbe 2026-09-13): 84 → 70 → 56 trước nhát đầu tiên của
     * người chơi; kết cục người chơi chết trong khi quái còn 31/130.
     */
    @Test
    @DisplayName("Phủ đầu 2 nhát: THUA trước máu theo cấp (lý do V46 hạ aggro 6 → 3), nay thắng")
    void twoOpeningHitsLoseTheDuel() {
        CombatConstants.CombatStats player = outerDisciple();
        int playerAtk = player.atk() + NOVICE_SET_ATK;
        int playerDef = player.def() + NOVICE_SET_DEF;

        int playerHitsNeeded = hitsToKill(RAIDER_HP, hit(playerAtk, RAIDER_DEF));
        int raiderHitsLeft = hitsToKill(hpAtFirstSwing(2), hit(RAIDER_ATK, playerDef));

        assertThat(hpAtFirstSwing(2)).isEqualTo(78);
        // Lượt thua 2026-09-13 là với 100 máu nền; từ máu theo cấp (65(d), +2/cấp) Cấp
        // 10 có thêm 18 máu.
        // V58 hạ atk 18 → 15 nên nay dư thêm nữa.
        assertThat(raiderHitsLeft).isGreaterThan(playerHitsNeeded);
    }

    /**
     * Luật hình học cho điểm spawn MỚI của V45 — dữ liệu thật thì
     * {@code PostgresMonsterCatalogAdapterIntegrationTest} gác (toạ độ sống trong DB, không trong
     * code); ô này giữ PHÉP TÍNH cạnh phép tính cân bằng, vì hai thứ phải đọc cùng nhau: trận tay
     * đôi chỉ đúng khi người chơi thật sự đánh MỘT con.
     */
    @Test
    @DisplayName("Linh mạch (53, V57) đủ xa tán tu (37) và thám tử (69) theo CẢ BA luật hình học")
    void theSpiritVeinIsAQuietPlaceToFightIn() {
        // V46 hạ `vein_raider` xuống aggro 3, nhưng hai HÀNG XÓM (tán tu @34, thám tử @66) vẫn giữ
        // aggro 6 mặc định V25 — và luật "đi ngang không kéo hai con" phụ thuộc con ĐAU NHẤT, nên
        // 6 ở đây là cận trên có chủ đích chứ không phải số cũ sót lại.
        float neighbourAggroRadiusMeters = 6f;
        float leashRadiusMeters = 10f;
        float meleeAttackRangeMeters = 2f;
        float patrolRadiusMeters = 3f; // mặc định V25

        // V56/V57 dời 47 → 53 (và hàng xóm 34 → 37, 66 → 69): vế tuần tra (64(b)) đòi > 15m.
        float toBandit = Math.abs(53f - 37f);
        float toScout = Math.abs(69f - 53f);

        // V29 — đi ngang không kéo được hai con cùng lúc.
        assertThat(toBandit).isGreaterThan(neighbourAggroRadiusMeters + neighbourAggroRadiusMeters);
        assertThat(toScout).isGreaterThan(neighbourAggroRadiusMeters + neighbourAggroRadiusMeters);
        // V32 — đứng lại đánh thì con bên cạnh không với tới nơi.
        assertThat(toBandit).isGreaterThan(leashRadiusMeters + meleeAttackRangeMeters);
        assertThat(toScout).isGreaterThan(leashRadiusMeters + meleeAttackRangeMeters);
        // 64(b) — con đang bị đánh còn đi tuần, người chơi đánh theo nó.
        assertThat(toBandit).isGreaterThan(patrolRadiusMeters + leashRadiusMeters + meleeAttackRangeMeters);
        assertThat(toScout).isGreaterThan(patrolRadiusMeters + leashRadiusMeters + meleeAttackRangeMeters);
    }

    /**
     * ✅ <b>ĐẠT luật biên thật từ V58 (lô 14, chủ dự án duyệt 2026-10-17)</b> — hạ ATK quái, xem bảng ở
     * đầu file migration. Đoạn dưới là LỊCH SỬ vì sao trước đó không có bộ chỉ số nào đạt.
     *
     * <p>Tới linh mạch 84 máu, bộ Tạp Dịch vs đoạt mạch 130/18/12: ta 4 nhát, nó 6 nhát ⇒ biên giấy
     * 2, biên thật −1. KHÔNG có bộ chỉ số nào đạt luật mà vẫn giữ
     * {@link #theRaiderShouldBeAStepAboveTheTrialConstruct()} (máu = 130, atk &gt; 15, def &gt; 10):
     * đạt biên thật +2 đòi sát thương mỗi nhát ≤10 ⇒ {@code atk ≤ 13}. Dừng, báo cáo.
     *
     * <p>Ô này KHOÁ đúng con số vi phạm hiện tại, để bất kỳ ai đổi chỉ số loài này đều phải dừng lại
     * đối chiếu với luật biên thật thay vì lặng lẽ giữ trận ở dưới ngưỡng.
     */
    @Test
    @DisplayName("Luật biên thật: đoạt mạch ĐẠT — biên thật +2 (V58 atk 18 → 15; trước đó +1)")
    void theSpiritVeinDuelViolatesTheRealMarginRule() {
        CombatConstants.CombatStats player = outerDisciple();
        DuelMargin duel = new DuelMargin(
                player.atk() + NOVICE_SET_ATK,
                player.def() + NOVICE_SET_DEF,
                arrivalHp(),
                RAIDER_HP,
                RAIDER_ATK,
                RAIDER_DEF);

        assertThat(duel.paperMargin()).isEqualTo(5);
        assertThat(duel.realMargin()).isEqualTo(2);
        assertThat(duel.meetsRealMarginRule()).isTrue();
    }
}
