package dev.kage.tools.loadtest.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Parse tham số dòng lệnh — thuần logic, không cần server. Điểm dễ sai nhất là {@code moveToX}:
 * "không truyền" và "truyền 0" phải KHÁC nhau, vì 0 là một toạ độ hợp lệ trong world.
 */
final class ProbeConfigTest {

    @Test
    void shouldUseDefaults_whenNoArgumentGiven() {
        ProbeConfig config = ProbeConfig.parse(new String[0]);

        assertThat(config.host()).isEqualTo("127.0.0.1");
        assertThat(config.port()).isEqualTo(7777);
        assertThat(config.username()).isEqualTo("kagetest");
        assertThat(config.characterId()).isZero();
        assertThat(config.moveToX()).isEmpty();
        assertThat(config.attackTarget()).isEqualTo(ProbeConfig.ATTACK_TARGET_NONE);
        assertThat(config.observeSeconds()).isEqualTo(10);
    }

    @Test
    void shouldDistinguishAbsentMoveTarget_fromExplicitZero() {
        assertThat(ProbeConfig.parse(new String[] {"--observeSeconds=3"}).moveToX())
                .isEmpty();

        assertThat(ProbeConfig.parse(new String[] {"--moveToX=0"}).moveToX()).hasValue(0.0);
    }

    @Test
    void shouldParseNegativeAndFractionalCoordinates() {
        assertThat(ProbeConfig.parse(new String[] {"--moveToX=-3.5"}).moveToX()).hasValue(-3.5);
    }

    @Test
    void shouldReadEveryScenarioArgument() {
        ProbeConfig config = ProbeConfig.parse(new String[] {
            "--host=10.0.0.2",
            "--port=7788",
            "--username=probe1",
            "--characterId=3",
            "--characterName=Kagemaru",
            "--moveToX=24",
            "--attackTarget=nearest",
            "--attackIntervalMillis=500",
            "--observeSeconds=15"
        });

        assertThat(config.host()).isEqualTo("10.0.0.2");
        assertThat(config.port()).isEqualTo(7788);
        assertThat(config.username()).isEqualTo("probe1");
        assertThat(config.characterId()).isEqualTo(3L);
        assertThat(config.characterName()).isEqualTo("Kagemaru");
        assertThat(config.moveToX()).hasValue(24.0);
        assertThat(config.attacksNearest()).isTrue();
        assertThat(config.attackIntervalMillis()).isEqualTo(500);
        assertThat(config.observeSeconds()).isEqualTo(15);
    }

    @Test
    void shouldTreatExplicitEntityIdAsAttackTarget() {
        ProbeConfig config = ProbeConfig.parse(new String[] {"--attackTarget=-7"});

        assertThat(config.attacksNearest()).isFalse();
        assertThat(config.explicitAttackTargetId()).hasValue(-7L);
    }

    @Test
    void shouldHaveNoTarget_whenAttackTargetIsNone() {
        assertThat(ProbeConfig.parse(new String[0]).explicitAttackTargetId()).isEmpty();
        assertThat(ProbeConfig.parse(new String[] {"--attackTarget=nearest"}).explicitAttackTargetId())
                .isEmpty();
    }

    /** Gõ sai phải NỔ, không được âm thầm biến thành "không đánh" — sai lặng lẽ là thứ đắt nhất. */
    @Test
    void shouldFailLoudly_whenAttackTargetIsNeitherKeywordNorNumber() {
        ProbeConfig config = ProbeConfig.parse(new String[] {"--attackTarget=neerest"});

        assertThatThrownBy(config::explicitAttackTargetId).isInstanceOf(NumberFormatException.class);
    }
}
