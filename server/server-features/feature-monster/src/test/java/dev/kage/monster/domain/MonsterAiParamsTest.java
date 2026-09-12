package dev.kage.monster.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * ADR-039 D1/D3 — {@link MonsterAiParams} là hàng rào THỨ HAI sau {@code CHECK} của migration V25.
 * Cần cả hai: {@code CHECK} gác dữ liệu vào DB, record này gác mọi đường khác (test dựng tay,
 * catalog dựng trong bộ nhớ, một adapter tương lai).
 */
class MonsterAiParamsTest {

    /** Bộ mặc định trước ADR-039 — bằng đúng {@code AiConstants.DEFAULTS} và DEFAULT của V25. */
    private static final MonsterAiParams DEFAULTS = new MonsterAiParams(6f, 10f, 3f, 1.5f, 3f, 3f, false);

    @Test
    void shouldTreatZeroAggroRadiusAsPassive() {
        // ADR-039 D4: 0 KHÔNG phải giá trị vô lý cần chặn — nó là cách `training_dummy` thôi đuổi
        // theo người chơi, không cần thêm cờ `is_passive` nào.
        MonsterAiParams dummy = new MonsterAiParams(0f, 10f, 3f, 1.5f, 0f, 3f, false);

        assertThat(dummy.isPassive()).isTrue();
        assertThat(DEFAULTS.isPassive()).isFalse();
    }

    @Test
    void shouldRejectLeashSmallerThanAggro() {
        // Bất biến THẬT: aggro đo từ quái tới NGƯỜI CHƠI, leash đo từ quái tới ĐIỂM SPAWN. Nếu
        // leash < aggro thì quái đuổi vài mét → Return → về tới nơi lại thấy người chơi trong tầm
        // aggro ⇒ dao động Chase/Return VÔ HẠN, quái rung tại chỗ, không bao giờ đánh được ai.
        assertThatThrownBy(() -> new MonsterAiParams(12f, 10f, 3f, 1.5f, 3f, 3f, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leashRadiusMeters");
    }

    @Test
    void shouldRejectPatrolRadiusOutsideLeash() {
        assertThatThrownBy(() -> new MonsterAiParams(6f, 10f, 3f, 1.5f, 11f, 3f, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("patrolRadiusMeters");
    }

    @Test
    void shouldRejectNonPositiveSpeedsAndDurations() {
        // Tốc độ 0 = con quái aggro rồi đứng hình mãi mãi; idle 0 = đổi trạng thái mỗi tick.
        assertThatThrownBy(() -> new MonsterAiParams(6f, 10f, 0f, 1.5f, 3f, 3f, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonsterAiParams(6f, 10f, 3f, 0f, 3f, 3f, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonsterAiParams(6f, 10f, 3f, 1.5f, 3f, 0f, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeRadii() {
        assertThatThrownBy(() -> new MonsterAiParams(-1f, 10f, 3f, 1.5f, 3f, 3f, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonsterAiParams(6f, 10f, 3f, 1.5f, -1f, 3f, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNaN() {
        // Mọi so sánh với NaN đều false, nên `value <= 0` KHÔNG bắt được nó — một cột REAL hỏng sẽ
        // lọt vào tick thread rồi biến vị trí quái thành NaN vĩnh viễn.
        assertThatThrownBy(() -> new MonsterAiParams(Float.NaN, 10f, 3f, 1.5f, 3f, 3f, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonsterAiParams(6f, 10f, Float.NaN, 1.5f, 3f, 3f, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
