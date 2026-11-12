package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.hud.ChatPanel;
import dev.kage.protocol.GuildMemberView;

/** Lô 45 phía client — xếp cống hiến (ADR-287), thu gọn khung chat (ADR-292), combo (ADR-293), cỡ chữ (ADR-294). */
class Lot45ClientTest {

    @Test
    void ranksContributionDescendingThenByName() {
        List<GuildMemberView> ranked = GuildContributionCoordinator.ranked(List.of(
                GuildMemberView.newBuilder().setName("B").setContribution(5).build(),
                GuildMemberView.newBuilder().setName("A").setContribution(5).build(),
                GuildMemberView.newBuilder().setName("C").setContribution(9).build()));
        assertThat(ranked).extracting(GuildMemberView::getName).containsExactly("C", "A", "B");
    }

    @Test
    void chatFadesOnlyInTheLastSeconds() {
        assertThat(ChatPanel.idleAlpha(0f, 12f)).isEqualTo(1f);
        assertThat(ChatPanel.idleAlpha(10f, 12f)).isEqualTo(1f);
        assertThat(ChatPanel.idleAlpha(11f, 12f)).isEqualTo(0.5f);
        assertThat(ChatPanel.idleAlpha(13f, 12f)).isZero();
        assertThat(ChatPanel.idleAlpha(1_000f, 0f)).isEqualTo(1f);
    }

    @Test
    void parsesIdleSecondsAndOffWords() {
        assertThat(ChatLayoutCoordinator.parseIdleSeconds("30")).isEqualTo(OptionalInt.of(30));
        assertThat(ChatLayoutCoordinator.parseIdleSeconds("2")).isEmpty();
        assertThat(ChatLayoutCoordinator.parseIdleSeconds("99999")).isEmpty();
        assertThat(ChatLayoutCoordinator.parseIdleSeconds("-5")).isEmpty();
        assertThat(ChatLayoutCoordinator.isOff("tắt")).isTrue();
        assertThat(ChatLayoutCoordinator.isOff("off")).isTrue();
        assertThat(ChatLayoutCoordinator.isOff("30")).isFalse();
    }

    @Test
    void parsesFontSizes() {
        assertThat(ChatLayoutCoordinator.FontSize.parse("lớn")).contains(ChatLayoutCoordinator.FontSize.LARGE);
        assertThat(ChatLayoutCoordinator.FontSize.parse("small")).contains(ChatLayoutCoordinator.FontSize.SMALL);
        assertThat(ChatLayoutCoordinator.FontSize.parse("to")).isEmpty();
    }

    @Test
    void comboCountsWithinWindowAndResets() {
        ComboCounter combo = new ComboCounter();
        assertThat(combo.hit()).isEqualTo(1);
        combo.tick(1f);
        assertThat(combo.hit()).isEqualTo(2);
        combo.tick(2.5f);
        assertThat(combo.hit()).isEqualTo(3);
        combo.tick(ComboCounter.WINDOW_SECONDS + 0.1f);
        assertThat(combo.current()).isZero();
        assertThat(combo.hit()).isEqualTo(1);
    }
}
