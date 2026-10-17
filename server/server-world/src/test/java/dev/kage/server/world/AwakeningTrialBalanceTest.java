package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cân bằng của quest thứ TƯ — đệ tử tạp dịch vs {@code trial_construct} (quest
 * {@code awakening_trial}, seed V33, STORY.md §6 arc 1 nhịp ĐÓNG).
 *
 * <p><b>Cái mới so với {@link OldArchiveDuelBalanceTest}.</b> Hai lớp cân bằng trước tính trận đấu
 * trên MÁU ĐẦY. Playtest 2026-09-06 cho thấy giả định đó sai với thế giới thật: đi bộ tới chỗ đánh
 * nhau tốn máu, và {@code server-world} KHÔNG có hồi máu ngoài chiến đấu, nên máu lúc CHẠM MẶT mới
 * là đầu vào đúng của bài toán. Đó chính là lỗ hổng V32 phải dời cả hai điểm spawn để vá.
 *
 * <p>Lớp này vì vậy tính trên số ĐO ĐƯỢC chứ không trên số đầy: {@link #ARRIVAL_HP} = 78 là kết quả
 * đo bằng {@code gameProbe} ngày 2026-09-06 (đi x=0 → x=-47, hai cỗ thạch vệ mỗi cỗ đánh trúng một
 * nhát 20 sát thương). Máu đầy vẫn được kiểm, nhưng chỉ như một cận trên.
 *
 * <p>Nó KHÔNG mô phỏng AI, khoảng cách hay né/bạo kích ngẫu nhiên — chỉ trả lời đúng một câu: với
 * sát thương TRUNG BÌNH mỗi bên, ai hết máu trước?
 */
class AwakeningTrialBalanceTest {

    /** Chỉ số nền của nhân vật mới: 10/10/10/10 (V-migration seed, giống mọi nhân vật vừa tạo). */
    private static final int BASE_STAT = 10;

    /**
     * Cấp 8 — cấp người chơi đứng vào lúc nhận quest 4 theo {@code ExperienceCurve}: cộng dồn EXP
     * thưởng của ba quest trước cộng EXP quái mà chính chúng bắt hạ được 2.705, tức Cấp 8 (mốc Cấp
     * 8 là 2.800... sát dưới, nên thực tế người chơi hay ở Cấp 7-8 tuỳ có cày thêm con nào không).
     * Cấp KHÔNG vào công thức derive (xem javadoc {@code CharacterBaseStats}); nó có mặt ở đây để
     * ghi rõ giả định.
     */
    private static final int LEVEL_AT_QUEST_FOUR = 8;

    /** Trọn bộ "Tạp Dịch" (`novice_*` + `wooden_sword`) — cộng dồn 7 món theo `item_template`. */
    private static final int NOVICE_SET_ATK = 7;

    private static final int NOVICE_SET_DEF = 7;
    private static final int NOVICE_SET_HP = 18;

    /**
     * Máu lúc CHẠM MẶT cỗ máy khảo hạch, ĐO THẬT bằng {@code gameProbe} 2026-09-06.
     *
     * <p>Đi bộ từ tông môn (x=0) sang sân khảo hạch (x=-47) phải lách qua hai cỗ thạch vệ ở -20 và
     * -33; mỗi cỗ kịp ra đúng MỘT nhát (aggro 6m, đuổi 3 m/s trong khi người chơi đi 5 m/s), mỗi
     * nhát 20 sát thương lên DEF 27 ⇒ 118 - 40 = 78. Con số này là ĐẦU VÀO của thiết kế chỉ số ở
     * V33, không phải kết quả kiểm lại sau.
     */
    private static final int ARRIVAL_HP = 78;

    /** `monster_template` hàng `trial_construct` (seed V33) — nguồn duy nhất của ba số này. */
    private static final int CONSTRUCT_HP = 130;

    private static final int CONSTRUCT_ATK = 15;
    private static final int CONSTRUCT_DEF = 10;

    /** `monster_template` hàng `archive_warden` (seed V30) — loài của quest 3, mốc so sánh. */
    private static final int WARDEN_ATK = 26;

    private static final int WARDEN_HP = 120;

    /** Giáp giảm sát thương kiểu LMHT/Liên Quân, đúng công thức {@code CombatConstants#resolveAttack}. */
    private static int hit(int attack, int defence) {
        return Math.round(attack * (100f / (100f + defence)));
    }

    private static int hitsToKill(int hitPoints, int damagePerHit) {
        return (int) Math.ceil((double) hitPoints / damagePerHit);
    }

    /**
     * Hệ phái là {@code NONE}, và ở quest 4 chi tiết đó là CHÍNH ĐỀ TÀI chứ không phải chi tiết phụ:
     * linh căn chỉ thức tỉnh ở Lv10 (GDD §5, STORY.md §6), mà Lv10 là thứ quest này đưa người chơi
     * TỚI. Nói cách khác đây là trận đánh cuối cùng của arc 1 mà người chơi còn chưa có hệ phái.
     */
    private static CombatConstants.CombatStats novice() {
        return CombatConstants.deriveCombatStats(
                new CharacterBaseStats(BASE_STAT, BASE_STAT, BASE_STAT, BASE_STAT, Element.NONE, LEVEL_AT_QUEST_FOUR));
    }

    @Test
    @DisplayName("Tới nơi với 78 máu vẫn THẮNG — 4 nhát so với 7, biên 3 nhát")
    void gearedNoviceShouldWinTheTrialEvenAfterTheWalk() {
        CombatConstants.CombatStats player = novice();

        int playerAtk = player.atk() + NOVICE_SET_ATK;
        int playerDef = player.def() + NOVICE_SET_DEF;

        int playerHitsFor = hitsToKill(CONSTRUCT_HP, hit(playerAtk, CONSTRUCT_DEF));
        int constructHitsFor = hitsToKill(ARRIVAL_HP, hit(CONSTRUCT_ATK, playerDef));

        // Hai bên chung nhịp đánh (CombatConstants.ATTACK_INTERVAL_SECONDS = 1 đòn/giây), nên "ít
        // nhát hơn" cũng chính là "chết sau".
        assertThat(playerHitsFor).isEqualTo(4);
        assertThat(constructHitsFor).isEqualTo(7);
    }

    /**
     * Vì sao biên phải RỘNG, và vì sao đây là một quyết định chứ không phải một con số cho vui.
     *
     * <p>ROADMAP mục 64 ghi lại đúng một lời cảnh báo từ playtest quest 3: người chơi bắn 91% trúng
     * ({@code hitPercent = (90 + agi × 0,1)/100}), nên một trận thắng sát nút là một trận có thể
     * thua vì hai lần hụt. Quest 3 là như vậy thật (thắng còn 38/118, và có một lượt đo thua đúng
     * vì hụt hai nhát). Nhịp ĐÓNG của một arc không nên là một lần tung đồng xu, nên trận này giữ
     * biên 3 nhát: hụt hai nhát vẫn thắng.
     *
     * <p>Cái khó của quest 4 nằm ở QUÃNG ĐƯỜNG (40 máu, {@link #ARRIVAL_HP}), không nằm ở trận đấu
     * — và lời thoại của Chu Hoành nói thẳng điều đó với người chơi.
     */
    @Test
    @DisplayName("Biên đủ rộng để hụt hai nhát vẫn thắng — không kém trận quest 3")
    void theTrialShouldNotBeACoinFlipTheWayTheArchiveDuelWas() {
        CombatConstants.CombatStats player = novice();
        int playerAtk = player.atk() + NOVICE_SET_ATK;
        int playerDef = player.def() + NOVICE_SET_DEF;
        int playerHp = CombatConstants.characterMaxHp(LEVEL_AT_QUEST_FOUR, NOVICE_SET_HP);

        int trialMargin = hitsToKill(ARRIVAL_HP, hit(CONSTRUCT_ATK, playerDef))
                - hitsToKill(CONSTRUCT_HP, hit(playerAtk, CONSTRUCT_DEF));
        // Quest 3 tính trên máu ĐẦY vì đường đi phía tây của V32 không tốn máu nào.
        int archiveMargin =
                hitsToKill(playerHp, hit(WARDEN_ATK, playerDef)) - hitsToKill(WARDEN_HP, hit(playerAtk, 14));

        assertThat(trialMargin).isEqualTo(3);
        assertThat(archiveMargin).isEqualTo(3);
        // Trước máu theo cấp (65(d), +2/cấp) quest 4 rộng hơn hẳn quest 3; nay quest 3
        // (Cấp 5) cũng được thêm 8 máu nên bằng nhau.
        assertThat(trialMargin).isGreaterThanOrEqualTo(archiveMargin);
    }

    /**
     * Ranh giới V30 đặt cho quest 3 vẫn còn hiệu lực ở quest 4: bộ "Tạp Dịch" 700 vàng là điều kiện
     * CẦN của nửa sau arc 1. Tay không thì ngay cả cỗ máy nương tay nhất cũng không qua được — vì
     * người chơi tay không cũng ăn nhiều sát thương hơn hẳn dọc đường (22/nhát thay vì 20).
     */
    @Test
    @DisplayName("TAY KHÔNG: thắng sát 1 nhát trên giấy, biên thật −2 — vẫn phải sắm đồ trước")
    void barehandedNoviceShouldStillFailTheTrial() {
        CombatConstants.CombatStats player = novice();

        int barehandedArrivalHp = CombatConstants.characterMaxHp(LEVEL_AT_QUEST_FOUR, 0)
                - 2 * hit(WARDEN_ATK, player.def()); // 114 - 2×22 = 70
        int playerHitsFor = hitsToKill(CONSTRUCT_HP, hit(player.atk(), CONSTRUCT_DEF));
        int constructHitsFor = hitsToKill(barehandedArrivalHp, hit(CONSTRUCT_ATK, player.def()));

        assertThat(barehandedArrivalHp).isEqualTo(70);
        assertThat(playerHitsFor).isEqualTo(5);
        assertThat(constructHitsFor).isEqualTo(6);
        // Từ máu theo cấp (65(d), +2/cấp) tay không thắng 1 nhát TRÊN GIẤY — luật biên
        // thật (DuelMargin) mới là trọng tài:
        // 1 − phủ đầu 2 − hụt 1 = −2, nên bộ Tạp Dịch vẫn là điều kiện cần.
        int realMargin = constructHitsFor - playerHitsFor - DuelMargin.OPENING_HITS - DuelMargin.DODGE_RESERVE;
        assertThat(realMargin).isEqualTo(-2).isLessThan(DuelMargin.REQUIRED_REAL_MARGIN);
    }

    /**
     * Cỗ máy khảo hạch là một bậc thang theo HƯỚNG KHÁC, không phải một con thạch vệ nữa.
     *
     * <p>Ba loài biết đánh trước nó đều đi theo một trục: đánh mạnh dần (8 → 22 → 26 → 40). Con này
     * bẻ trục: máu CAO NHẤT arc 1 mà đòn NHẸ NHẤT trong bốn loài biết đánh. Bài thi là "ngươi trụ
     * được bao lâu", đúng luận đề của Chu Hoành (STORY.md §5) — chứ không phải "ngươi chịu đòn giỏi
     * cỡ nào".
     */
    @Test
    @DisplayName("Dai hơn thạch vệ nhưng đánh nhẹ hơn — bậc thang theo hướng khác, không phải to hơn")
    void theTrialConstructShouldTestEnduranceNotLethality() {
        assertThat(CONSTRUCT_HP).isGreaterThan(WARDEN_HP);
        assertThat(CONSTRUCT_ATK).isLessThan(WARDEN_ATK);
    }

    /**
     * Luật hình học áp cho điểm spawn MỚI của V33 — dữ liệu thật thì
     * {@code PostgresMonsterCatalogAdapterIntegrationTest} gác (toạ độ sống trong DB, không trong
     * code); ô này giữ PHÉP TÍNH cạnh phép tính cân bằng, vì hai thứ đó phải đọc cùng nhau: trận
     * tay đôi chỉ đúng khi người chơi thật sự đánh MỘT con.
     *
     * <p>Ba luật, cùng một cặp hàng xóm: sân khảo hạch @-52 (V56/V57 dời từ -47) và thạch vệ gần nhất @-36.
     */
    @Test
    @DisplayName("Sân khảo hạch (-52, V57) đủ xa thạch vệ (-36) theo CẢ BA luật hình học")
    void theTrialGroundIsAQuietPlaceToFightIn() {
        float aggroRadiusMeters = 6f; // mặc định V25, cả hai loài giữ nguyên
        float leashRadiusMeters = 10f;
        float meleeAttackRangeMeters = 2f;
        float patrolRadiusMeters = 3f; // mặc định V25
        float distanceToNearestWarden = Math.abs(-52f - -36f);

        assertThat(distanceToNearestWarden).isEqualTo(16f);
        // V29 — đi ngang không kéo được hai con cùng lúc.
        assertThat(distanceToNearestWarden).isGreaterThan(aggroRadiusMeters + aggroRadiusMeters);
        // V32 — đứng lại đánh thì con bên cạnh không với tới nơi.
        assertThat(distanceToNearestWarden).isGreaterThan(leashRadiusMeters + meleeAttackRangeMeters);
        // 64(b) — cỗ máy đi tuần 3m và người chơi đánh theo: ở -47 (14m) thạch vệ đuổi tới, 2/2 chết.
        assertThat(distanceToNearestWarden)
                .isGreaterThan(patrolRadiusMeters + leashRadiusMeters + meleeAttackRangeMeters);
    }

    /**
     * 🔴 <b>VI PHẠM luật biên thật (chủ dự án chốt 2026-10-01, {@link DuelMargin}) — CHƯA chỉnh số,
     * chờ chủ dự án.</b>
     *
     * <p>Tới nơi 78 máu (đo thật), bộ Tạp Dịch vs cỗ khảo hạch 130/15/10: ta 4 nhát, nó 7 nhát ⇒
     * biên giấy 3, biên thật 0. Mọi bộ chỉ số đạt luật mà vẫn giữ
     * {@link #barehandedNoviceShouldStillFailTheTrial()} đều phải đổi {@code max_hp}, mà
     * {@code SpiritVeinDuelBalanceTest#theRaiderShouldBeAStepAboveTheTrialConstruct} khoá
     * {@code vein_raider} CÙNG máu 130 và đánh/giáp HƠN nó — kéo theo cả {@code vein_raider} (bản
     * thân cũng vi phạm và không có lời giải trên bậc thang). Dừng, báo cáo.
     *
     * <p>Ô này KHOÁ đúng con số vi phạm hiện tại, để bất kỳ ai đổi chỉ số loài này đều phải dừng lại
     * đối chiếu với luật biên thật thay vì lặng lẽ giữ trận ở dưới ngưỡng.
     */
    @Test
    @DisplayName("🔴 Luật biên thật: cỗ khảo hạch ĐANG vi phạm — biên thật 0, cần +2 (chờ chủ dự án)")
    void theTrialViolatesTheRealMarginRule() {
        CombatConstants.CombatStats player = novice();
        DuelMargin duel = new DuelMargin(
                player.atk() + NOVICE_SET_ATK,
                player.def() + NOVICE_SET_DEF,
                ARRIVAL_HP,
                CONSTRUCT_HP,
                CONSTRUCT_ATK,
                CONSTRUCT_DEF);

        assertThat(duel.paperMargin()).isEqualTo(3);
        assertThat(duel.realMargin()).isZero();
        assertThat(duel.meetsRealMarginRule()).isFalse();
    }
}
