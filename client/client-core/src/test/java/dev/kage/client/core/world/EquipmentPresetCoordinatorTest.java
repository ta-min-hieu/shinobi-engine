package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ApplyEquipmentPresetResponse;
import dev.kage.protocol.EquipmentPresetOutcome;

/** ADR-175 — cú pháp {@code /bodo} và câu báo mặc bộ. */
final class EquipmentPresetCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void parseShouldReadListSaveAndApply() {
        assertThat(EquipmentPresetCoordinator.parse("")).isInstanceOf(EquipmentPresetCoordinator.Intent.List.class);
        assertThat(EquipmentPresetCoordinator.parse("luu 2")).isEqualTo(new EquipmentPresetCoordinator.Intent.Save(2));
        assertThat(EquipmentPresetCoordinator.parse("Lưu 1")).isEqualTo(new EquipmentPresetCoordinator.Intent.Save(1));
        assertThat(EquipmentPresetCoordinator.parse("3")).isEqualTo(new EquipmentPresetCoordinator.Intent.Apply(3));
        assertThat(EquipmentPresetCoordinator.parse("mac 1")).isEqualTo(new EquipmentPresetCoordinator.Intent.Apply(1));
        assertThat(EquipmentPresetCoordinator.parse("luu")).isInstanceOf(EquipmentPresetCoordinator.Intent.Usage.class);
        assertThat(EquipmentPresetCoordinator.parse("abc")).isInstanceOf(EquipmentPresetCoordinator.Intent.Usage.class);
    }

    @Test
    void describeApplyShouldMentionMissingItems() {
        ApplyEquipmentPresetResponse ok = ApplyEquipmentPresetResponse.newBuilder()
                .setOutcome(EquipmentPresetOutcome.EQUIPMENT_PRESET_OUTCOME_OK)
                .setEquipped(3)
                .build();
        assertThat(EquipmentPresetCoordinator.describeApply(KageI18n.load(), ok))
                .contains("3");
        assertThat(EquipmentPresetCoordinator.describeApply(
                        KageI18n.load(), ok.toBuilder().setMissing(2).build()))
                .contains("3")
                .contains("2");
        assertThat(EquipmentPresetCoordinator.describeApply(
                        KageI18n.load(),
                        ok.toBuilder()
                                .setOutcome(EquipmentPresetOutcome.EQUIPMENT_PRESET_OUTCOME_EMPTY_PRESET)
                                .build()))
                .doesNotStartWith("preset.");
    }
}
