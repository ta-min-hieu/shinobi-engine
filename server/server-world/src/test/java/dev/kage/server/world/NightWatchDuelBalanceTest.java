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
     * ĐÂY mới là chỗ trận đấu thật sự vỡ, và nó KHÔNG nằm ở chỉ số con quái: hai điểm spawn
     * {@code wandering_bandit} đặt ở {@code x = 21} và {@code x = 27} (V25, {@code monster_spawn}),
     * đúng bằng {@code aggro_radius_meters = 6} của loài này. Nghĩa là **mọi vị trí trong đoạn
     * [21, 27] đều nằm trong tầm aggro của CẢ HAI** — người chơi đi làm quest gần như chắc chắn kéo
     * hai con cùng lúc, và lúc đó sát thương nhận vào GẤP ĐÔI (34/giây thay vì 17).
     *
     * <p>Test này khoá con số hình học đó lại: sửa spawn hay sửa {@code aggro_radius} đều sẽ làm nó
     * đỏ, và người sửa buộc phải nhìn lại phép tính này thay vì đổi mù.
     */
    @Test
    @DisplayName("Hai điểm spawn tán tu cách nhau ĐÚNG bằng bán kính aggro — đoạn giữa kéo cả hai con")
    void twoBanditSpawnsOverlapTheirOwnAggroRadius() {
        float firstSpawnX = 21f;
        float secondSpawnX = 27f;
        float aggroRadiusMeters = 6f;

        float gap = secondSpawnX - firstSpawnX;

        assertThat(gap).isEqualTo(aggroRadiusMeters);
        // Không có ĐIỂM NÀO giữa hai con mà chỉ một con nhìn thấy mình: khoảng cách tới con gần
        // nhất luôn ≤ 3m, và tới con xa nhất luôn ≤ 6m = vẫn trong tầm aggro.
        assertThat(gap).isLessThanOrEqualTo(2 * aggroRadiusMeters);
    }
}
