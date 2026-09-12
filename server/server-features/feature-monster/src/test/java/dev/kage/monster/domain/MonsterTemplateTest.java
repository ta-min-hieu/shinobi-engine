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
        return new MonsterAiParams(6f, 10f, 3f, 1.5f, 3f, 3f, false);
    }

    @Test
    void shouldAcceptTemplateWithoutDropTable() {
        // dropTableCode null = loài không rơi gì. Hợp lệ, không phải lỗi — adapter dùng LEFT JOIN
        // đúng vì lý do này (INNER JOIN sẽ nuốt mất cả loài khỏi catalog).
        MonsterTemplate template = new MonsterTemplate(
                "stone_ogre", "hud.monster.stone_ogre", "mob_training-dummy", stats(), ai(), 90L, null, null);

        assertThat(template.dropTableCode()).isNull();
        assertThat(template.expReward()).isEqualTo(90L);
    }

    @Test
    void shouldRejectBlankIdentifiers() {
        assertThatThrownBy(() -> new MonsterTemplate(" ", "n", "m", stats(), ai(), 0L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonsterTemplate("c", " ", "m", stats(), ai(), 0L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonsterTemplate("c", "n", " ", stats(), ai(), 0L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeExpReward() {
        assertThatThrownBy(() -> new MonsterTemplate("c", "n", "m", stats(), ai(), -1L, null, null))
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

    /**
     * ADR-048 — hành vi boss VẮNG MẶT là trường hợp thường (mọi quái FSM), nên {@code isBoss()}
     * phải nói đúng cả hai chiều. Đây là thứ quyết định một loài có được cấp {@code BossAiComponent}
     * lúc spawn hay không, và nếu nó sai theo chiều "false" thì boss đứng im như quái thường —
     * không có gì đỏ, chỉ có một trận đấu nhạt.
     */
    @Test
    void shouldReportBossOnlyWhenBehaviourPresent() {
        MonsterTemplate quaiThuong = new MonsterTemplate("stone_ogre", "n", "m", stats(), ai(), 90L, null, null);
        assertThat(quaiThuong.isBoss()).isFalse();
        assertThat(quaiThuong.bossBehaviour()).isNull();

        MonsterTemplate boss = new MonsterTemplate(
                "sample_boss",
                "n",
                "m",
                stats(),
                ai(),
                200L,
                null,
                new MonsterBossBehaviour("/scripts/boss/sample_boss.js", 0.5f, 3.0f));
        assertThat(boss.isBoss()).isTrue();
        assertThat(boss.bossBehaviour().scriptRef()).isEqualTo("/scripts/boss/sample_boss.js");
    }
}
