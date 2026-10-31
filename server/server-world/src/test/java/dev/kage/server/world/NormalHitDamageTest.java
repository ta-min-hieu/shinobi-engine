package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

/** ADR-124 — "còn một đòn" phải đúng bằng sát thương đòn thường trúng không bạo kích của pipeline thật. */
class NormalHitDamageTest {

    @Test
    void shouldEqualANonCritHitOfTheRealPipeline() {
        CombatConstants.CombatStats attacker =
                new CombatConstants.CombatStats(50, 0, 0, 0, 1f, 0f, 0f, 1.5f, Element.NONE);
        CombatConstants.CombatStats defender =
                new CombatConstants.CombatStats(0, 0, 25, 0, 0f, 0f, 0f, 1f, Element.NONE);

        CombatConstants.AttackResult hit = CombatConstants.resolveAttack(attacker, defender, new SplittableRandom(1));

        assertThat(hit.miss()).isFalse();
        assertThat(hit.crit()).isFalse();
        assertThat(CombatConstants.normalHitDamage(attacker, defender))
                .isEqualTo(hit.damage())
                .isEqualTo(40);
    }
}
