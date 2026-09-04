package dev.kage.monster.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MonsterTemplateTest {

    private static MonsterStats stats() {
        return new MonsterStats(1, 50, 8, 0, 5, 3, 90, 0, 0, "NONE");
    }

    /** Bộ mặc định của dự án trước ADR-039 — bằng đúng `AiConstants.DEFAULTS` và DEFAULT của V25. */
    private static MonsterAiParams ai() {
        return new MonsterAiParams(6f, 10f, 3f, 1.5f, 3f, 3f);
    }

    @Test
    void shouldAcceptTemplateWithoutDropTable() {
        // dropTableCode null = loài không rơi gì. Hợp lệ, không phải lỗi — adapter dùng LEFT JOIN
        // đúng vì lý do này (INNER JOIN sẽ nuốt mất cả loài khỏi catalog).
        MonsterTemplate template = new MonsterTemplate(
                "stone_ogre", "hud.monster.stone_ogre", "mob_training-dummy", stats(), ai(), 90L, null);

        assertThat(template.dropTableCode()).isNull();
        assertThat(template.expReward()).isEqualTo(90L);
    }

    @Test
    void shouldRejectBlankIdentifiers() {
        assertThatThrownBy(() -> new MonsterTemplate(" ", "n", "m", stats(), ai(), 0L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonsterTemplate("c", " ", "m", stats(), ai(), 0L, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonsterTemplate("c", "n", " ", stats(), ai(), 0L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeExpReward() {
        assertThatThrownBy(() -> new MonsterTemplate("c", "n", "m", stats(), ai(), -1L, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectPercentOutsideRange() {
        // 0..100 chứ không phải 0..1 — nhầm hai thang này là con quái né 100% hoặc không bao giờ
        // trúng, và không có gì báo.
        assertThatThrownBy(() -> new MonsterStats(1, 50, 8, 0, 5, 3, 101, 0, 0, "NONE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonsterStats(1, 50, 8, 0, 5, 3, 90, -1, 0, "NONE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveMaxHp() {
        assertThatThrownBy(() -> new MonsterStats(1, 0, 8, 0, 5, 3, 90, 0, 0, "NONE"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveRespawnSeconds() {
        assertThatThrownBy(() -> new MonsterSpawnPoint("c", MonsterSpawnPoint.MAIN_WORLD_CODE, 8f, 0f, 0f))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
