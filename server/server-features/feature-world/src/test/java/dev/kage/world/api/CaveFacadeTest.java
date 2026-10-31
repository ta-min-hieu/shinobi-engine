package dev.kage.world.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.world.domain.CavePort;
import dev.kage.world.domain.CaveRules;

/** ADR-129 — lời mời thăm động phủ (RAM, 10 phút) + thưởng bày trí cộng theo danh mục. */
class CaveFacadeTest {

    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    private static final long OWNER = 1L;
    private static final long GUEST = 2L;

    @Test
    void shouldLetInvitedGuestsInForTenMinutesOnly() {
        CaveFacade caves = new CaveFacade(new DisabledLikePort());

        assertThat(caves.mayEnter(OWNER, OWNER, NOW))
                .as("chủ luôn vào nhà mình")
                .isTrue();
        assertThat(caves.mayEnter(GUEST, OWNER, NOW)).isFalse();
        caves.invite(OWNER, GUEST, NOW);
        assertThat(caves.mayEnter(GUEST, OWNER, NOW.plusSeconds(60))).isTrue();
        assertThat(caves.mayEnter(OWNER, GUEST, NOW.plusSeconds(60)))
                .as("lời mời một chiều")
                .isFalse();
        assertThat(caves.mayEnter(GUEST, OWNER, NOW.plus(CaveRules.INVITE_TTL))).isFalse();
    }

    @Test
    void shouldSumBonusesOfPlacedDecorationsOnly() {
        CaveFacade caves = new CaveFacade(new DisabledLikePort());
        CavePort.Cave cave = new CavePort.Cave(2, NOW, List.of("pine", "pond"));

        assertThat(caves.bonusPercentOf(cave)).isEqualTo(25);
        assertThat(caves.stored(cave, NOW.plusSeconds(3_600))).isEqualTo(CaveRules.perHour(2, 25));
    }

    /** Danh mục ba món; các lệnh ghi không dùng tới trong test này. */
    private static final class DisabledLikePort implements CavePort {
        @Override
        public List<Decoration> catalog() {
            return List.of(
                    new Decoration("pine", "k.pine", 100L, 10),
                    new Decoration("lamp", "k.lamp", 100L, 20),
                    new Decoration("pond", "k.pond", 100L, 15));
        }

        @Override
        public Cave cave(long characterId, Instant now) {
            return new Cave(1, now, List.of());
        }

        @Override
        public Result collect(long characterId, Instant now) {
            return new Result(Outcome.NOTHING_TO_COLLECT, 0L);
        }

        @Override
        public Result upgrade(long characterId, Instant now) {
            return new Result(Outcome.MAX_LEVEL, 0L);
        }

        @Override
        public Result decorate(long characterId, String code, Instant now) {
            return new Result(Outcome.UNKNOWN_DECORATION, 0L);
        }
    }
}
