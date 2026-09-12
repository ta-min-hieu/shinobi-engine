package dev.kage.monster.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * ADR-048 — hàng rào cho ba con số mà một hàng {@code monster_template} sai có thể mang vào.
 *
 * <p>Cả ba thứ ở đây đều hỏng theo kiểu CÂM nếu lọt qua: script rỗng thì boss chạy như quái FSM
 * thường (không có gì đỏ, chỉ là một trận đấu nhạt), nhịp quyết định bằng 0 thì hỏi script MỖI tick
 * 60Hz thay vì mỗi 0,5s, và hồi chiêu bằng 0 thì chiêu đặc biệt không bao giờ hồi. Không cái nào tự
 * báo lỗi lúc chạy — nên chúng phải bị chặn ngay lúc dựng.
 */
class MonsterBossBehaviourTest {

    @Test
    void shouldAcceptTheNumbersSampleBossActuallyUses() {
        // Đúng hàng V41 của `sample_boss`, vốn chép nguyên từ hằng số Java trước ADR-048.
        MonsterBossBehaviour behaviour = new MonsterBossBehaviour("/scripts/boss/sample_boss.js", 0.5f, 3.0f);

        assertThat(behaviour.scriptRef()).isEqualTo("/scripts/boss/sample_boss.js");
        assertThat(behaviour.decisionIntervalSeconds()).isEqualTo(0.5f);
        assertThat(behaviour.skillCooldownSeconds()).isEqualTo(3.0f);
    }

    @Test
    void shouldRejectMissingScriptRef() {
        // CHECK của V41 đã cấm `is_boss AND ai_script_ref IS NULL` ở tầng DB; đây là hàng rào thứ
        // hai, cho cả những template dựng tay trong test và cho một adapter đọc sai cột.
        assertThatThrownBy(() -> new MonsterBossBehaviour(null, 0.5f, 3.0f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonsterBossBehaviour(" ", 0.5f, 3.0f))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositivePacing() {
        // 0 không phải "mặc định" mà là một con số vô nghĩa lọt qua từ hàng DB sai — xem javadoc
        // `MonsterBossBehaviour.requirePositive`.
        assertThatThrownBy(() -> new MonsterBossBehaviour("/scripts/boss/sample_boss.js", 0f, 3.0f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonsterBossBehaviour("/scripts/boss/sample_boss.js", -0.5f, 3.0f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonsterBossBehaviour("/scripts/boss/sample_boss.js", 0.5f, 0f))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonsterBossBehaviour("/scripts/boss/sample_boss.js", 0.5f, -3.0f))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
