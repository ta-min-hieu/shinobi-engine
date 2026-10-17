package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cân bằng của quest thứ BA — đệ tử tạp dịch vs {@code archive_warden} (quest
 * {@code clear_old_archive}, seed V30, STORY.md §6 arc 1 nhịp "gieo mầm").
 *
 * <p><b>Vì sao có lớp này.</b> Cùng lý do {@link NightWatchDuelBalanceTest} tồn tại, nhưng lần này
 * dùng TRƯỚC chứ không phải sau: chỉ số của loài mới được CHỌN từ phép tính ở đây, không phải chọn
 * bằng cảm giác rồi mới đi kiểm. Câu hỏi quyết định là *"quest 3 có qua nổi bằng đường chơi thường
 * không"*, và đường chơi thường ở arc 1 là trọn bộ "Tạp Dịch" 700 vàng mua ở Tàng Bảo Các — KHÔNG
 * phải bộ "Hạ Nhẫn" 10.900 vàng, thứ nằm ngoài tầm với của arc 1.
 *
 * <p>Chính phép tính này là lý do quest 3 KHÔNG dùng lại {@code stone_ogre} (loài mạnh nhất đang
 * có): xem {@link #theStoneOgreWouldHaveBeenUnbeatableForThisArc()}.
 *
 * <p>Nó KHÔNG mô phỏng AI, khoảng cách hay né/bạo kích ngẫu nhiên — chỉ trả lời đúng một câu: với
 * sát thương TRUNG BÌNH mỗi bên, ai hết máu trước?
 */
class OldArchiveDuelBalanceTest {

    /** Chỉ số nền của nhân vật mới: 10/10/10/10 (V-migration seed, giống mọi nhân vật vừa tạo). */
    private static final int BASE_STAT = 10;

    /**
     * Cấp 5 — cấp người chơi đứng vào lúc nhận quest 3 theo {@code ExperienceCurve} (100 × cấp):
     * 500 EXP quest 1 + 800 EXP quest 2 + EXP quái đã giết dọc đường vượt mốc 1.000 của Cấp 5.
     * Cấp KHÔNG vào công thức derive (xem javadoc {@code CharacterBaseStats}); nó có mặt ở đây để
     * ghi rõ giả định, và vì đó là mốc {@code required_level} của bộ "Hạ Nhẫn" (V22).
     */
    private static final int LEVEL_AT_QUEST_THREE = 5;

    /** Trọn bộ "Tạp Dịch" (`novice_*` + `wooden_sword`) — cộng dồn 7 món theo `item_template`. */
    private static final int NOVICE_SET_ATK = 7;

    private static final int NOVICE_SET_DEF = 7;
    private static final int NOVICE_SET_HP = 18;

    /** `monster_template` hàng `archive_warden` (seed V30) — nguồn duy nhất của ba số này. */
    private static final int WARDEN_HP = 120;

    private static final int WARDEN_ATK = 26;
    private static final int WARDEN_DEF = 14;

    /** `monster_template` hàng `wandering_bandit` (seed V23) — mốc so sánh của quest 2. */
    private static final int BANDIT_HP = 90;

    private static final int BANDIT_DEF = 10;

    /** `monster_template` hàng `stone_ogre` (seed V23) — loài mạnh nhất đang có, KHÔNG dùng ở arc 1. */
    private static final int OGRE_HP = 200;

    private static final int OGRE_ATK = 40;
    private static final int OGRE_DEF = 25;

    /** Giáp giảm sát thương kiểu LMHT/Liên Quân, đúng công thức {@code CombatConstants#resolveAttack}. */
    private static int hit(int attack, int defence) {
        return Math.round(attack * (100f / (100f + defence)));
    }

    private static int hitsToKill(int hitPoints, int damagePerHit) {
        return (int) Math.ceil((double) hitPoints / damagePerHit);
    }

    /**
     * Hệ phái là {@code NONE} chứ không phải {@code HOA} như {@link NightWatchDuelBalanceTest}, và
     * đó là chi tiết ĐÚNG với arc 1: linh căn chỉ thức tỉnh ở Lv10 (GDD §5, STORY.md §6), nên nhân
     * vật đang làm quest 3 chưa có hệ. Hai passive của Hỏa cộng vào SKILL_DMG và bạo kích, không
     * cộng vào ATK/DEF — nên con số đòn thường dưới đây giống hệt nhau ở cả hai hệ.
     */
    private static CombatConstants.CombatStats novice() {
        return CombatConstants.deriveCombatStats(
                new CharacterBaseStats(BASE_STAT, BASE_STAT, BASE_STAT, BASE_STAT, Element.NONE, LEVEL_AT_QUEST_THREE));
    }

    @Test
    @DisplayName("Mặc trọn bộ Tạp Dịch THẮNG thạch vệ — 4 nhát so với 7")
    void gearedNoviceShouldWinTheArchiveDuel() {
        CombatConstants.CombatStats player = novice();

        int playerAtk = player.atk() + NOVICE_SET_ATK;
        int playerDef = player.def() + NOVICE_SET_DEF;
        int playerHp = CombatConstants.characterMaxHp(LEVEL_AT_QUEST_THREE, NOVICE_SET_HP);

        int playerHitsFor = hitsToKill(WARDEN_HP, hit(playerAtk, WARDEN_DEF));
        int wardenHitsFor = hitsToKill(playerHp, hit(WARDEN_ATK, playerDef));

        // Hai bên dùng CHUNG một nhịp đánh (CombatConstants.ATTACK_INTERVAL_SECONDS = 1 đòn/giây),
        // nên "ít nhát hơn" cũng chính là "chết sau" — không cần mô phỏng thời gian.
        assertThat(playerHitsFor).isEqualTo(4);
        assertThat(wardenHitsFor).isEqualTo(7);
        assertThat(playerHitsFor).isLessThan(wardenHitsFor);
    }

    @Test
    @DisplayName("TAY KHÔNG thì hoà 5 với 5 — hoà nghĩa là chết, nên phải sắm đồ trước")
    void barehandedNoviceShouldNotSurviveTheArchive() {
        CombatConstants.CombatStats player = novice();

        int playerHitsFor = hitsToKill(WARDEN_HP, hit(player.atk(), WARDEN_DEF));
        int wardenHitsFor =
                hitsToKill(CombatConstants.characterMaxHp(LEVEL_AT_QUEST_THREE, 0), hit(WARDEN_ATK, player.def()));

        // Cùng nhịp đánh mà cùng số nhát thì người chơi KHÔNG thắng — đây là ranh giới cố ý: quest 2
        // (4 so với 6 tay không) còn qua được tay không, quest 3 thì không.
        assertThat(playerHitsFor).isEqualTo(5);
        assertThat(wardenHitsFor).isEqualTo(5);
        assertThat(playerHitsFor).isGreaterThanOrEqualTo(wardenHitsFor);
    }

    @Test
    @DisplayName("Thạch vệ là bậc thang TRÊN tán tu, không phải bản sao đổi tên")
    void theArchiveWardenShouldBeAStepAboveTheBandit() {
        CombatConstants.CombatStats player = novice();
        int playerAtk = player.atk() + NOVICE_SET_ATK;

        int hitsForBandit = hitsToKill(BANDIT_HP, hit(playerAtk, BANDIT_DEF));
        int hitsForWarden = hitsToKill(WARDEN_HP, hit(playerAtk, WARDEN_DEF));

        // 3 so với 4: dai hơn thật, nhưng chưa tới mức đổi cả cách chơi.
        assertThat(hitsForBandit).isEqualTo(3);
        assertThat(hitsForWarden).isGreaterThan(hitsForBandit);
    }

    /**
     * Vì sao quest 3 phải THÊM một loài thay vì dùng lại {@code stone_ogre} — câu trả lời là số,
     * không phải sở thích.
     *
     * <p>Quỷ đá chỉ hạ được khi mặc trọn bộ "Hạ Nhẫn" (V22: ATK 30→59, DEF 20→43, HP 100→158), mà
     * bộ đó giá <b>10.900 vàng</b> (V21 §4). Với ~12 vàng mỗi mạng tán tu, đó là khoảng 900 con —
     * không phải "đường chơi thường" của arc 1 theo bất kỳ nghĩa nào.
     */
    @Test
    @DisplayName("Quỷ đá thì mặc trọn bộ Tạp Dịch vẫn THUA — 7 nhát so với 5, nên arc 1 không dùng nó")
    void theStoneOgreWouldHaveBeenUnbeatableForThisArc() {
        CombatConstants.CombatStats player = novice();

        int playerAtk = player.atk() + NOVICE_SET_ATK;
        int playerDef = player.def() + NOVICE_SET_DEF;
        int playerHp = CombatConstants.characterMaxHp(LEVEL_AT_QUEST_THREE, NOVICE_SET_HP);

        int playerHitsFor = hitsToKill(OGRE_HP, hit(playerAtk, OGRE_DEF));
        int ogreHitsFor = hitsToKill(playerHp, hit(OGRE_ATK, playerDef));

        assertThat(playerHitsFor).isEqualTo(7);
        assertThat(ogreHitsFor).isEqualTo(5);
        assertThat(playerHitsFor).isGreaterThan(ogreHitsFor);
    }

    /**
     * Luật hình học của V29 áp cho cặp spawn MỚI của V30 — dữ liệu thật thì
     * {@code PostgresMonsterCatalogAdapterIntegrationTest
     * .shouldKeepAggroSpawnsApartEnoughToPullOneAtATime} gác (toạ độ sống trong DB, không trong
     * code); ô này giữ lại PHÉP TÍNH cạnh phép tính cân bằng, vì hai thứ đó phải đọc cùng nhau:
     * trận tay đôi chỉ đúng khi người chơi thật sự đánh MỘT con.
     */
    @Test
    @DisplayName("Hai điểm spawn thạch vệ (-20, -36 — V57) đủ xa nhau và đủ xa mọi hàng xóm CÓ AGGRO")
    void theNewArchiveSpawnsKeepTheDuelOneOnOne() {
        float aggroRadiusMeters = 6f; // mặc định V25, thạch vệ giữ nguyên
        float combinedReach = aggroRadiusMeters + aggroRadiusMeters;

        // ⚠️ Ba con số của bản đầu (60/73 cạnh quỷ đá 47) đã CHẾT từ V32: playtest đo được đường đi
        // phía đông tốn 65 máu, và quỷ đá@47 với leash 10 còn ra đòn kết liễu người chơi đang đánh
        // thạch vệ@60. Chủ dự án chọn dời cả hai sang phía TÂY (-20 / -33) — nơi lời thoại của Chu
        // Hoành vốn đã nói từ đầu. Test này giữ nguyên tên mà không đổi số suốt một ngày: nó VẪN
        // XANH vì ba phép trừ kia là số học thuần, chỉ có điều chúng không còn nói về thế giới thật.
        // Đó là dạng mục ruỗng khó thấy nhất — test xanh mô tả một bản đồ không còn tồn tại.
        assertThat(Math.abs(-20f - -36f)).isGreaterThan(combinedReach);
        // Hàng xóm CÓ AGGRO gần nhất về hai phía: sân khảo hạch @-47 (V33) và tán tu @21 (V29).
        // Mộc nhân @8/@14 không tính — aggro 0, chúng không bao giờ tự đánh ai.
        assertThat(Math.abs(-36f - -52f)).isGreaterThan(combinedReach);
        assertThat(Math.abs(-20f - 21f)).isGreaterThan(combinedReach);
    }

    /**
     * 🔴 <b>VI PHẠM luật biên thật (chủ dự án chốt 2026-10-01, {@link DuelMargin}) — CHƯA chỉnh số,
     * chờ chủ dự án.</b>
     *
     * <p>Bộ Tạp Dịch vs thạch vệ 120/26/14: ta 4 nhát, nó 6 nhát ⇒ biên giấy 2, biên thật −1.
     * Quét toàn bộ không gian chỉ số: mọi bộ cho biên thật ≥2 đều làm
     * {@link #barehandedNoviceShouldNotSurviveTheArchive()} hỏng (tay không cũng thắng) VÀ phá bậc
     * thang tán tu (90) &lt; thạch vệ &lt; cỗ khảo hạch (130 máu, atk 15). Bộ Tạp Dịch chỉ hơn tay
     * không +7/+7/+18 — khoảng đó quá hẹp để vừa "phải sắm đồ" vừa "thắng chắc". Đây là câu hỏi
     * THIẾT KẾ (đổi bộ đồ hoặc bỏ một luật), không phải một con số.
     *
     * <p>Ô này KHOÁ đúng con số vi phạm hiện tại, để bất kỳ ai đổi chỉ số loài này đều phải dừng lại
     * đối chiếu với luật biên thật thay vì lặng lẽ giữ trận ở dưới ngưỡng.
     */
    @Test
    @DisplayName(
            "🔴 Luật biên thật: thạch vệ ĐANG vi phạm — biên thật 0 (máu theo cấp; trước đó −1), cần +2 (chờ chủ dự án)")
    void theArchiveDuelViolatesTheRealMarginRule() {
        CombatConstants.CombatStats player = novice();
        DuelMargin duel = new DuelMargin(
                player.atk() + NOVICE_SET_ATK,
                player.def() + NOVICE_SET_DEF,
                CombatConstants.characterMaxHp(LEVEL_AT_QUEST_THREE, NOVICE_SET_HP),
                WARDEN_HP,
                WARDEN_ATK,
                WARDEN_DEF);

        assertThat(duel.paperMargin()).isEqualTo(3);
        assertThat(duel.realMargin()).isEqualTo(0);
        assertThat(duel.meetsRealMarginRule()).isFalse();
    }
}
