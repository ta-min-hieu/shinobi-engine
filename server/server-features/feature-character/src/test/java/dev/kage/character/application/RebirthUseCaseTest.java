package dev.kage.character.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.character.domain.ExperienceCurve;
import dev.kage.character.domain.RebirthOutcome;
import dev.kage.character.domain.RebirthPolicy;
import dev.kage.character.domain.RebirthPort;
import dev.kage.character.domain.Transaction;

/** ADR-089 — chuyển thế chỉ ở cấp tối đa, tối đa 5 lần, về cấp 1. */
class RebirthUseCaseTest {

    /** Một nhân vật trong bộ nhớ. */
    private static final class FakePort implements RebirthPort {
        int level;
        int count;

        FakePort(int level, int count) {
            this.level = level;
            this.count = count;
        }

        @Override
        public Optional<Progress> lock(Transaction tx, long characterId) {
            return Optional.of(new Progress(level, count));
        }

        @Override
        public void rebirth(Transaction tx, long characterId) {
            level = 1;
            count++;
        }

        @Override
        public int rebirthCountOf(long characterId) {
            return count;
        }
    }

    private static RebirthUseCase useCase(FakePort port) {
        return new RebirthUseCase(port, action -> action.accept(null));
    }

    @Test
    void shouldRebirthAtMaxLevel_backToLevelOne() {
        FakePort port = new FakePort(ExperienceCurve.MAX_LEVEL, 0);

        RebirthUseCase.Result result = useCase(port).rebirth(1L);

        assertThat(result).isEqualTo(new RebirthUseCase.Result(RebirthOutcome.OK, 1));
        assertThat(port.level).isEqualTo(1);
    }

    @Test
    void shouldRefuseBelowMaxLevel_andAfterTheLastRebirth() {
        assertThat(useCase(new FakePort(ExperienceCurve.MAX_LEVEL - 1, 0))
                        .rebirth(1L)
                        .outcome())
                .isEqualTo(RebirthOutcome.LEVEL_TOO_LOW);
        FakePort done = new FakePort(ExperienceCurve.MAX_LEVEL, RebirthPolicy.MAX_REBIRTHS);
        assertThat(useCase(done).rebirth(1L))
                .isEqualTo(new RebirthUseCase.Result(RebirthOutcome.MAX_REBIRTHS, RebirthPolicy.MAX_REBIRTHS));
        assertThat(done.level).isEqualTo(ExperienceCurve.MAX_LEVEL);
    }
}
