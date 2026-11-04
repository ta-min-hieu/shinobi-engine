package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Faction;
import dev.kage.protocol.InspectCharacterOutcome;
import dev.kage.protocol.InspectCharacterResponse;

/** ADR-171 — dòng đầu cửa sổ soi (tên, cảnh giới, Lực Chiến) và câu báo khi người đó ẩn. */
final class InspectCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void headerShouldCarryNameAndCombatPower() {
        String header = InspectCoordinator.header(
                KageI18n.load(),
                InspectCharacterResponse.newBuilder()
                        .setName("ĐạoHữu01")
                        .setLevel(20)
                        .setFaction(Faction.FACTION_HOA)
                        .setCombatPower(987)
                        .build());

        assertThat(header).contains("ĐạoHữu01").contains("987");
    }

    @Test
    void hiddenShouldNameThePerson() {
        String line = InspectCoordinator.describeRefusal(
                KageI18n.load(),
                InspectCharacterResponse.newBuilder()
                        .setOutcome(InspectCharacterOutcome.INSPECT_CHARACTER_OUTCOME_HIDDEN)
                        .setName("BaoDo01")
                        .build());

        assertThat(line).contains("BaoDo01");
    }
}
