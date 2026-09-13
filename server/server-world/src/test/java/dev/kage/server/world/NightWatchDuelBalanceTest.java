package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cân bằng của trận đấu quyết định cao trào arc 1: đệ tử tạp dịch vs {@code wandering_bandit}
 * (quest {@code night_watch_bandit}, STORY.md §6).
 *
 * <p><b>Vì sao có lớp này</b>: playtest tay 2026-09-05 chết 4 lần liên tiếp trước con tán tu và tôi
 * suýt đề xuất hạ chỉ số của nó. Nhưng bằng chứng lúc đó chỉ là ẢNH CHỤP — cùng loại bằng chứng đã
 * một lần khiến tôi báo nhầm một lỗi di chuyển trong chính ngày hôm đó (ROADMAP mục 55). Lớp này
 * hỏi thẳng CÔNG THỨC thay vì hỏi màn hình, nên câu trả lời tái lập được và không phụ thuộc vào
 * việc tôi bấm chuột trúng hay trượt.
 *
 * <p>Nó KHÔNG mô phỏng AI, khoảng cách hay né/bạo kích ngẫu nhiên — chỉ trả lời đúng một câu:
 * <em>với sát thương TRUNG BÌNH mỗi bên, ai hết máu trước?</em> Đó là câu quyết định "quest này có
 * qua nổi bằng đường chơi thường không".
 */
class NightWatchDuelBalanceTest {

    /** Chỉ số nền của nhân vật mới: 10/10/10/10 (V-migration seed, giống mọi nhân vật vừa tạo). */
    private static final int BASE_STAT = 10;

    /** Trọn bộ "Tạp Dịch" (`novice_*`) mua ở Tàng Bảo Các — cộng dồn 7 món theo `item_template`. */
    private static final int NOVICE_SET_ATK = 7;

    private static final int NOVICE_SET_DEF = 7;
    private static final int NOVICE_SET_HP = 18;

    /** `monster_template` hàng `wandering_bandit` (seed V25) — nguồn duy nhất của ba số này. */
    private static final int BANDIT_HP = 90;

    private static final int BANDIT_ATK = 22;
    private static final int BANDIT_DEF = 10;

    /** Giáp giảm sát thương kiểu LMHT/Liên Quân, đúng công thức {@code CombatConstants#resolveAttack}. */
    private static int hit(int attack, int defence) {
        return Math.round(attack * (100f / (100f + defence)));
    }

    private static int hitsToKill(int hitPoints, int damagePerHit) {
        return (int) Math.ceil((double) hitPoints / damagePerHit);
    }

    @Test
    @DisplayName("Cấp 4 mặc trọn bộ Tạp Dịch THẮNG tán tu trong tay đôi — 3 nhát so với 7")
    void gearedNoviceShouldWinTheNightWatchDuel() {
        CombatConstants.CombatStats player = CombatConstants.deriveCombatStats(
                new CharacterBaseStats(BASE_STAT, BASE_STAT, BASE_STAT, BASE_STAT, Element.HOA, 4));

        int playerAtk = player.atk() + NOVICE_SET_ATK;
        int playerDef = player.def() + NOVICE_SET_DEF;
        int playerHp = CombatConstants.CHARACTER_MAX_HP + NOVICE_SET_HP;

        int playerHitsFor = hitsToKill(BANDIT_HP, hit(playerAtk, BANDIT_DEF));
        int banditHitsFor = hitsToKill(playerHp, hit(BANDIT_ATK, playerDef));

        // Hai bên dùng CHUNG một nhịp đánh (CombatConstants.ATTACK_INTERVAL_SECONDS = 1 đòn/giây),
        // nên "ít nhát hơn" cũng chính là "chết sau" — không cần mô phỏng thời gian.
        assertThat(playerHitsFor).isEqualTo(3);
        assertThat(banditHitsFor).isEqualTo(7);
        assertThat(playerHitsFor).isLessThan(banditHitsFor);
    }

    @Test
    @DisplayName("KHÔNG có đồ thì trận đấu gần như hoà — đúng chỗ người chơi phải đi sắm trang bị")
    void barehandedNoviceShouldBeAtTheEdge() {
        CombatConstants.CombatStats player = CombatConstants.deriveCombatStats(
                new CharacterBaseStats(BASE_STAT, BASE_STAT, BASE_STAT, BASE_STAT, Element.HOA, 4));

        int playerHitsFor = hitsToKill(BANDIT_HP, hit(player.atk(), BANDIT_DEF));
        int banditHitsFor = hitsToKill(CombatConstants.CHARACTER_MAX_HP, hit(BANDIT_ATK, player.def()));

        // 4 so với 6: thắng được nhưng sát nút — đúng ý "đi sắm đồ trước" mà cửa hàng phục vụ.
        assertThat(playerHitsFor).isEqualTo(4);
        assertThat(banditHitsFor).isEqualTo(6);
    }

    /**
     * ĐÂY mới là chỗ trận đấu từng vỡ, và nó KHÔNG nằm ở chỉ số con quái: trước V29 hai điểm spawn
     * {@code wandering_bandit} đặt ở {@code x = 21} và {@code x = 27} — cách nhau đúng 6m trong khi
     * TỔNG tầm aggro của hai con là 12m, nên mọi vị trí ở giữa kéo cả hai và sát thương nhận vào
     * gấp đôi (34/giây thay vì 17). Quest {@code night_watch_bandit} lại dẫn người chơi vào đúng
     * đoạn đó.
     *
     * <p>V29 giãn thành 21 / 34 / 47. Ô test này giữ lại PHÉP TÍNH để nó không bị quên; còn dữ liệu
     * thật thì {@code PostgresMonsterCatalogAdapterIntegrationTest
     * .shouldKeepAggroSpawnsApartEnoughToPullOneAtATime} gác, vì toạ độ sống trong DB chứ không
     * trong code.
     */
    @Test
    @DisplayName("Hai con cùng kéo được khi khoảng cách KHÔNG vượt tổng tầm aggro — luật của V29")
    void twoAggressiveSpawnsPullTogetherWhenCloserThanTheirCombinedAggro() {
        float aggroRadiusMeters = 6f;
        float combinedReach = aggroRadiusMeters + aggroRadiusMeters;

        // Bố cục CŨ (21 và 27): 6 <= 12 ⇒ có vị trí kéo cả hai.
        assertThat(27f - 21f).isLessThanOrEqualTo(combinedReach);
        // Bố cục V29 (21 và 34): 13 > 12 ⇒ không vị trí nào kéo được cả hai.
        assertThat(34f - 21f).isGreaterThan(combinedReach);
        // Và quỷ đá cũng phải rời ra theo, nếu không lỗi chỉ dời sang cặp tán tu #2 + quỷ đá.
        // V40 dời nó 47 → 80 (ra SAU hang bỏ hoang) vì playtest đo được một mình nó lấy 31 máu mỗi
        // lượt người chơi đi ngang, đủ để quest 8 thua chắc. V45 dời nó LẦN NỮA, 80 → 106, vì cùng
        // một lý do lặp lại ở quãng Lv10–18: nó nằm trên đường tới sào huyệt đoạt mạch @93. Luật
        // V29 vẫn phải đúng ở mỗi chỗ mới — đó là điều ô này gác, không phải con số cụ thể.
        assertThat(106f - 34f).isGreaterThan(combinedReach);
    }
}
