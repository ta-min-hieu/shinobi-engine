package dev.kage.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PetPolicyTest {

    @Test
    void expShouldCarryOverAcrossSeveralLevels() {
        // cấp 1→2 cần 100, 2→3 cần 200: 350 EXP từ 0 = cấp 3 còn 50.
        PetProgress progress = PetPolicy.gain(new PetProgress(7L, 9L, 1, 0L, false), 350L);

        assertThat(progress.level()).isEqualTo(3);
        assertThat(progress.expIntoLevel()).isEqualTo(50L);
        assertThat(progress.leveledUp()).isTrue();
        assertThat(progress.expToNext()).isEqualTo(300L);
    }

    @Test
    void smallGainShouldNotLevelUp() {
        PetProgress progress = PetPolicy.gain(new PetProgress(7L, 9L, 4, 10L, false), 20L);

        assertThat(progress.level()).isEqualTo(4);
        assertThat(progress.expIntoLevel()).isEqualTo(30L);
        assertThat(progress.leveledUp()).isFalse();
    }

    @Test
    void maxLevelShouldDropTheRest() {
        PetProgress progress = PetPolicy.gain(new PetProgress(7L, 9L, PetPolicy.MAX_LEVEL - 1, 0L, false), 1_000_000L);

        assertThat(progress.level()).isEqualTo(PetPolicy.MAX_LEVEL);
        assertThat(progress.expIntoLevel()).isZero();
        assertThat(progress.expToNext()).isZero();
        assertThat(PetPolicy.bonusLevel(1)).isZero();
        assertThat(PetPolicy.bonusLevel(5)).isEqualTo(4);
    }
}
