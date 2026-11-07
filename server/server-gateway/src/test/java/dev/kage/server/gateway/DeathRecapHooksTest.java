package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.DeathRecapEvent;

/** ADR-195 — gom đòn theo kẻ ra đòn, nhiều sát thương nhất trước, kỹ năng gây nhiều nhất được nêu. */
final class DeathRecapHooksTest {

    private static final long PLAYER = 7L;
    private static final long MONSTER = -3L;

    @Test
    void groupsByAttackerAndPicksTheTopSkill() {
        DeathRecapHooks hooks = new DeathRecapHooks(() -> 0L);

        DeathRecapEvent event = hooks.recap(
                PLAYER,
                List.of(
                        new DeathRecapHooks.Hit(MONSTER, 10, "", 1L),
                        new DeathRecapHooks.Hit(PLAYER, 30, "fireball", 2L),
                        new DeathRecapHooks.Hit(PLAYER, 20, "", 3L),
                        new DeathRecapHooks.Hit(PLAYER, 40, "fireball", 4L),
                        new DeathRecapHooks.Hit(MONSTER, 15, "", 5L)));

        assertThat(event.getKillerEntityId()).isEqualTo(PLAYER);
        assertThat(event.getWindowSeconds()).isEqualTo(DeathRecapHooks.WINDOW_SECONDS);
        assertThat(event.getEntriesList()).hasSize(2);
        assertThat(event.getEntries(0).getAttackerEntityId()).isEqualTo(PLAYER);
        assertThat(event.getEntries(0).getDamage()).isEqualTo(90);
        assertThat(event.getEntries(0).getHits()).isEqualTo(3);
        assertThat(event.getEntries(0).getTopSkillCode()).isEqualTo("fireball");
        assertThat(event.getEntries(0).getAttackerName())
                .as("chưa bind — không tra được tên")
                .isEmpty();
        assertThat(event.getEntries(1).getDamage()).isEqualTo(25);
        assertThat(event.getEntries(1).getTopSkillCode()).isEmpty();
    }

    @Test
    void ignoresSelfHitsAndZeroDamage_andForgets() {
        DeathRecapHooks hooks = new DeathRecapHooks(() -> 1_000L);
        hooks.onHit(1L, 1L, 50, "");
        hooks.onHit(1L, 2L, 0, "");
        hooks.forget(1L);
        hooks.onDefeated(1L, 2L, null); // không còn hàng đợi ⇒ không gửi, không chạm registry
    }
}
