package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.server.world.component.StatusEffect;
import dev.kage.server.world.component.StatusEffectComponent;
import dev.kage.server.world.component.StatusEffectType;

/** Phase 8 (4 hệ phái) — helper thuần đọc/ghi {@link StatusEffectComponent}, test không qua tick loop. */
class StatusEffectsTest {

    private static final CombatConstants.CombatStats BASE_STATS =
            new CombatConstants.CombatStats(30, 25, 20, 15, 0.9f, 0.1f, 0.1f, 1.5f, Element.HOA);

    @Test
    void shouldDetectStun_onlyWhenStunSlotPresent() {
        StatusEffectComponent status = new StatusEffectComponent();
        assertThat(StatusEffects.isStunned(status)).isFalse();

        status.slots[0] = new StatusEffect(StatusEffectType.STUN, 0f, 1f);
        assertThat(StatusEffects.isStunned(status)).isTrue();
    }

    @Test
    void shouldComputeMoveSpeedMultiplier_asOnePlusMagnitude() {
        StatusEffectComponent status = new StatusEffectComponent();
        assertThat(StatusEffects.moveSpeedMultiplier(status)).isEqualTo(1f);

        status.slots[0] = new StatusEffect(StatusEffectType.MOVE_SPEED_MOD, -0.4f, 4f);
        assertThat(StatusEffects.moveSpeedMultiplier(status)).isEqualTo(0.6f);
    }

    @Test
    void shouldNeverReturnNegativeMoveSpeedMultiplier_evenWithExtremeSlow() {
        StatusEffectComponent status = new StatusEffectComponent();
        status.slots[0] = new StatusEffect(StatusEffectType.MOVE_SPEED_MOD, -2f, 4f);
        assertThat(StatusEffects.moveSpeedMultiplier(status)).isEqualTo(0f);
    }

    @Test
    void shouldReturnSameStats_whenNoRelevantModifierActive() {
        StatusEffectComponent status = new StatusEffectComponent();
        CombatConstants.CombatStats result = StatusEffects.applyModifiers(BASE_STATS, status);
        assertThat(result).isEqualTo(BASE_STATS);
    }

    @Test
    void shouldApplyDefResCritModifiers_asPercentOfBase() {
        StatusEffectComponent status = new StatusEffectComponent();
        status.slots[0] = new StatusEffect(StatusEffectType.DEF_MOD, 0.4f, 6f); // +40% DEF
        status.slots[1] = new StatusEffect(StatusEffectType.RES_MOD, -0.2f, 6f); // -20% RES
        status.slots[2] = new StatusEffect(StatusEffectType.CRIT_MOD, 0.3f, 6f); // +30% CRIT

        CombatConstants.CombatStats result = StatusEffects.applyModifiers(BASE_STATS, status);

        assertThat(result.def()).isEqualTo(Math.round(20 * 1.4f));
        assertThat(result.res()).isEqualTo(Math.round(15 * 0.8f));
        assertThat(result.critPercent()).isEqualTo(0.4f);
        assertThat(result.atk()).isEqualTo(BASE_STATS.atk()); // không liên quan — giữ nguyên
    }

    @Test
    void shouldAbsorbDamageFromShieldPool_beforeAnyHpLoss() {
        StatusEffectComponent status = new StatusEffectComponent();
        status.slots[0] = new StatusEffect(StatusEffectType.SHIELD, 10f, 6f);

        int remaining = StatusEffects.absorbWithShield(status, 7);

        assertThat(remaining).isZero();
        assertThat(status.slots[0].magnitude).isEqualTo(3f);
    }

    @Test
    void shouldClearShieldSlot_whenPoolFullyDepleted() {
        StatusEffectComponent status = new StatusEffectComponent();
        status.slots[0] = new StatusEffect(StatusEffectType.SHIELD, 5f, 6f);

        int remaining = StatusEffects.absorbWithShield(status, 8);

        assertThat(remaining).isEqualTo(3);
        assertThat(status.slots[0]).isNull();
    }

    @Test
    void shouldPassThroughDamage_whenNoShieldActive() {
        StatusEffectComponent status = new StatusEffectComponent();
        assertThat(StatusEffects.absorbWithShield(status, 12)).isEqualTo(12);
    }

    @Test
    void shouldWriteEffectToFreeSlot() {
        StatusEffectComponent status = new StatusEffectComponent();

        StatusEffects.applyEffect(status, StatusEffectType.STUN, 0f, 1.5f);

        assertThat(status.slots[0]).isNotNull();
        assertThat(status.slots[0].type).isEqualTo(StatusEffectType.STUN);
        assertThat(status.slots[0].remainingSeconds).isEqualTo(1.5f);
    }

    @Test
    void shouldRefreshExistingSlot_whenSameTypeAlreadyActive_insteadOfStacking() {
        StatusEffectComponent status = new StatusEffectComponent();
        StatusEffects.applyEffect(status, StatusEffectType.MOVE_SPEED_MOD, -0.4f, 2f);
        StatusEffects.applyEffect(status, StatusEffectType.MOVE_SPEED_MOD, -0.5f, 5f);

        long activeMoveSpeedSlots = java.util.Arrays.stream(status.slots)
                .filter(e -> e != null && e.type == StatusEffectType.MOVE_SPEED_MOD)
                .count();
        assertThat(activeMoveSpeedSlots).isEqualTo(1);
        assertThat(status.slots[0].magnitude).isEqualTo(-0.5f);
        assertThat(status.slots[0].remainingSeconds).isEqualTo(5f);
    }

    @Test
    void shouldSilentlyDropEffect_whenAllSlotsFull() {
        StatusEffectComponent status = new StatusEffectComponent();
        status.slots[0] = new StatusEffect(StatusEffectType.STUN, 0f, 1f);
        status.slots[1] = new StatusEffect(StatusEffectType.DEF_MOD, 0.1f, 1f);
        status.slots[2] = new StatusEffect(StatusEffectType.RES_MOD, 0.1f, 1f);
        status.slots[3] = new StatusEffect(StatusEffectType.CRIT_MOD, 0.1f, 1f);

        StatusEffects.applyEffect(status, StatusEffectType.MOVE_SPEED_MOD, -0.5f, 3f);

        assertThat(java.util.Arrays.stream(status.slots)
                        .filter(e -> e != null && e.type == StatusEffectType.MOVE_SPEED_MOD))
                .isEmpty();
    }
}
