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

    private static final int RAIDER_ATK = 18;
    private static final int RAIDER_DEF = 12;

    /** `monster_template` hàng `wandering_bandit` (seed V23) — loài đứng dọc đường tới linh mạch. */
    private static final int BANDIT_ATK = 22;

    /** `monster_template` hàng `trial_construct` (seed V33) — loài của quest 4, bậc thang ngay dưới. */
    private static final int CONSTRUCT_HP = 130;

    private static final int CONSTRUCT_ATK = 15;
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
        return CombatConstants.CHARACTER_MAX_HP + NOVICE_SET_HP - (2 * hit(BANDIT_ATK, playerDef));
    }

    @Test
    @DisplayName("Tới linh mạch với 84 máu vẫn THẮNG — 4 nhát so với 6, biên 2 nhát")
    void gearedDiscipleShouldWinTheSpiritVeinDuel() {
        CombatConstants.CombatStats player = outerDisciple();

        int playerAtk = player.atk() + NOVICE_SET_ATK;
        int playerDef = player.def() + NOVICE_SET_DEF;

        int playerHitsFor = hitsToKill(RAIDER_HP, hit(playerAtk, RAIDER_DEF));
        int raiderHitsFor = hitsToKill(arrivalHp(), hit(RAIDER_ATK, playerDef));

        // Hai bên chung nhịp đánh (ATTACK_INTERVAL_SECONDS = 1 đòn/giây), nên "ít nhát hơn" cũng
        // chính là "chết sau" — không cần mô phỏng thời gian.
        assertThat(arrivalHp()).isEqualTo(84);
        assertThat(playerHitsFor).isEqualTo(4);
        assertThat(raiderHitsFor).isEqualTo(6);
        assertThat(raiderHitsFor - playerHitsFor).isEqualTo(2);
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
     * Luật hình học cho điểm spawn MỚI của V45 — dữ liệu thật thì
     * {@code PostgresMonsterCatalogAdapterIntegrationTest} gác (toạ độ sống trong DB, không trong
     * code); ô này giữ PHÉP TÍNH cạnh phép tính cân bằng, vì hai thứ phải đọc cùng nhau: trận tay
     * đôi chỉ đúng khi người chơi thật sự đánh MỘT con.
     */
    @Test
    @DisplayName("Linh mạch (47) đủ xa tán tu (34) và thám tử (66) theo CẢ HAI luật hình học")
    void theSpiritVeinIsAQuietPlaceToFightIn() {
        float aggroRadiusMeters = 6f;
        float leashRadiusMeters = 10f;
        float meleeAttackRangeMeters = 2f;

        float toBandit = Math.abs(47f - 34f);
        float toScout = Math.abs(66f - 47f);

        // V29 — đi ngang không kéo được hai con cùng lúc.
        assertThat(toBandit).isGreaterThan(aggroRadiusMeters + aggroRadiusMeters);
        assertThat(toScout).isGreaterThan(aggroRadiusMeters + aggroRadiusMeters);
        // V32 — đứng lại đánh thì con bên cạnh không với tới nơi.
        assertThat(toBandit).isGreaterThan(leashRadiusMeters + meleeAttackRangeMeters);
        assertThat(toScout).isGreaterThan(leashRadiusMeters + meleeAttackRangeMeters);
    }
}
