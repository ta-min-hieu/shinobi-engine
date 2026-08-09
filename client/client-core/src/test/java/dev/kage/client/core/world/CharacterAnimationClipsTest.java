package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CharacterAnimationClipsTest {

    @Test
    void isMoving_falseAtExactZeroVelocity() {
        assertThat(CharacterAnimationClips.isMoving(0f, 0f)).isFalse();
    }

    @Test
    void isMoving_falseForTinyFloatNoise() {
        // Sai số float sau chuẩn hoá vector hướng (WorldScreen.updateLocalMovementIntent) — không
        // được coi là "đang di chuyển" dù khác 0 tuyệt đối.
        assertThat(CharacterAnimationClips.isMoving(0.0001f, 0.0001f)).isFalse();
    }

    @Test
    void isMoving_trueAtRealisticMoveSpeed() {
        // Tốc độ di chuyển thật (WorldConstants.MOVE_SPEED_MPS = 5) đi chéo — chắc chắn trên ngưỡng.
        assertThat(CharacterAnimationClips.isMoving(3.5f, 3.5f)).isTrue();
    }

    @Test
    void isMoving_trueForSingleAxisMovement() {
        assertThat(CharacterAnimationClips.isMoving(5f, 0f)).isTrue();
        assertThat(CharacterAnimationClips.isMoving(0f, -5f)).isTrue();
    }

    @Test
    void loopingClipFor_selectsRunWhenMovingIdleOtherwise() {
        assertThat(CharacterAnimationClips.loopingClipFor(false, true)).isEqualTo(CharacterAnimationClips.RUN);
        assertThat(CharacterAnimationClips.loopingClipFor(false, false)).isEqualTo(CharacterAnimationClips.IDLE);
    }

    @Test
    void loopingClipFor_selectsJump_regardlessOfMoving_whenAirborne() {
        // ADR-033 — jump có ưu tiên cao nhất, kể cả khi đang nhảy NGANG (moving=true).
        assertThat(CharacterAnimationClips.loopingClipFor(true, true)).isEqualTo(CharacterAnimationClips.JUMP);
        assertThat(CharacterAnimationClips.loopingClipFor(true, false)).isEqualTo(CharacterAnimationClips.JUMP);
    }
}
