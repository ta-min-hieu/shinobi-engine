package dev.kage.world.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import dev.kage.world.domain.DungeonSweepPort;

/** ADR-162 — mã Bí Cảnh dị dạng (NUL, chữ hoa, rỗng) bị trả UNKNOWN_DUNGEON mà không chạm port (DB). */
final class DungeonSweepFacadeTest {

    @Test
    void malformedCodesNeverReachThePort() {
        AtomicInteger calls = new AtomicInteger();
        DungeonSweepFacade facade = new DungeonSweepFacade(
                (characterId, code, day, max) -> {
                    calls.incrementAndGet();
                    return DungeonSweepPort.Result.refused(DungeonSweepPort.Outcome.NOT_CLEARED, 0);
                },
                Clock.systemUTC());

        for (String bad : new String[] {"\0", "", "Bi_Canh", "a b", "x".repeat(65)}) {
            assertThat(facade.sweep(1L, bad).outcome()).isEqualTo(DungeonSweepPort.Outcome.UNKNOWN_DUNGEON);
        }
        assertThat(calls).hasValue(0);
        assertThat(facade.sweep(1L, "bandit_den").outcome()).isEqualTo(DungeonSweepPort.Outcome.NOT_CLEARED);
        assertThat(calls).hasValue(1);
    }
}
