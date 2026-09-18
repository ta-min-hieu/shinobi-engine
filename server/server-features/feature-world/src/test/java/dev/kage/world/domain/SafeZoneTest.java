package dev.kage.world.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** ADR-046 D3 — vùng an toàn: hàng rào ở constructor, song song với {@code CHECK} của V35. */
class SafeZoneTest {

    @Test
    void shouldRejectBlankCode() {
        assertThatThrownBy(() -> new SafeZone(" ", SafeZone.MAIN_WORLD_CODE, -15f, -10f, 5f, 10f, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankWorldCode() {
        assertThatThrownBy(() -> new SafeZone("sect_grounds", "", -15f, -10f, 5f, 10f, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Biên lộn ngược là hỏng ÂM THẦM: {@code Aabb.contains} trả false với MỌI điểm, nên vùng an
     * toàn đơn giản là không tồn tại và không có gì báo. Chặn ở đây VÀ ở {@code CHECK} của V35.
     */
    @Test
    void shouldRejectInvertedBounds() {
        assertThatThrownBy(() -> new SafeZone("sect_grounds", SafeZone.MAIN_WORLD_CODE, 5f, -10f, -15f, 10f, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SafeZone("sect_grounds", SafeZone.MAIN_WORLD_CODE, -15f, 10f, 5f, -10f, true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptDegenerateBounds_becauseAZeroWidthZoneIsMeaninglessButNotCorrupt() {
        // min == max là vùng một điểm: vô dụng nhưng không sai dữ liệu. Chặn nó là đoán ý người
        // viết migration; để nó qua thì playtest tự thấy "đứng đúng chỗ đó mới hồi máu".
        SafeZone point = new SafeZone("pin", SafeZone.MAIN_WORLD_CODE, 3f, 0f, 3f, 0f, true);

        assertThat(point.minX()).isEqualTo(point.maxX());
    }
}
