package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.DeathRecapEntry;
import dev.kage.protocol.DeathRecapEvent;

/** ADR-195 — tóm tắt khi chết thành các dòng chat: người chơi theo tên server, quái theo mã nhớ từ gói spawn. */
final class DeathRecapCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void namesPlayersAndMonsters() {
        DeathRecapEvent event = DeathRecapEvent.newBuilder()
                .setKillerEntityId(7L)
                .setWindowSeconds(10)
                .addEntries(DeathRecapEntry.newBuilder()
                        .setAttackerEntityId(7L)
                        .setAttackerName("Benkei")
                        .setDamage(120)
                        .setHits(4)
                        .setTopSkillCode("fireball"))
                .addEntries(DeathRecapEntry.newBuilder()
                        .setAttackerEntityId(-3L)
                        .setDamage(30)
                        .setHits(2))
                .addEntries(DeathRecapEntry.newBuilder()
                        .setAttackerEntityId(-9L)
                        .setDamage(5)
                        .setHits(1))
                .build();

        List<String> lines = DeathRecapCoordinator.describe(KageI18n.load(), event, Map.of(-3L, "wandering_bandit"));

        assertThat(lines).hasSize(4).allSatisfy(line -> assertThat(line).doesNotStartWith("death_recap."));
        assertThat(lines.get(1)).contains("Benkei").contains("120").doesNotContain("hud.skill.");
        assertThat(lines.get(2)).doesNotContain("hud.monster.");
    }
}
