package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.server.world.component.StatusEffectType;

/** Boss chịu khống chế với nửa thời lượng (README dòng 37vi, chủ dự án chốt 2026-10-11). */
class CrowdControlTest {

    private static final float DURATION = 4f;

    @Test
    void shouldHalveStunAndSlow_onABoss() {
        assertThat(CrowdControl.durationOn(true, StatusEffectType.STUN, 0f, DURATION))
                .isEqualTo(2f);
        assertThat(CrowdControl.durationOn(true, StatusEffectType.MOVE_SPEED_MOD, -0.4f, DURATION))
                .isEqualTo(2f);
    }

    /** Đối chứng: quái thường và người chơi chịu đủ thời lượng. */
    @Test
    void shouldKeepTheFullDuration_onAnythingThatIsNotABoss() {
        assertThat(CrowdControl.durationOn(false, StatusEffectType.STUN, 0f, DURATION))
                .isEqualTo(DURATION);
        assertThat(CrowdControl.durationOn(false, StatusEffectType.MOVE_SPEED_MOD, -0.4f, DURATION))
                .isEqualTo(DURATION);
    }

    /** Giảm giáp/kháng là làm yếu, không phải khống chế; tăng tốc cũng không phải. */
    @Test
    void shouldKeepTheFullDuration_forEffectsThatAreNotCrowdControl() {
        assertThat(CrowdControl.durationOn(true, StatusEffectType.DEF_MOD, -0.3f, DURATION))
                .isEqualTo(DURATION);
        assertThat(CrowdControl.durationOn(true, StatusEffectType.RES_MOD, -0.3f, DURATION))
                .isEqualTo(DURATION);
        assertThat(CrowdControl.durationOn(true, StatusEffectType.MOVE_SPEED_MOD, 0.3f, DURATION))
                .isEqualTo(DURATION);
    }
}
