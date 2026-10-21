package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.Faction;

/**
 * {@link FactionController} thuần Java, không libGDX — chạy được thẳng trong JUnit như
 * {@code GuildControllerTest}.
 */
class FactionControllerTest {

    private final FactionController controller = new FactionController();

    @Test
    @DisplayName("Chưa đủ Lv10: không bày lối vào chọn hệ")
    void hidesEntranceBelowLevelTen() {
        controller.onCharacterState(9, Faction.FACTION_HOA);

        assertThat(controller.canOffer()).isFalse();
    }

    @Test
    @DisplayName("Đủ Lv10 và còn ở hệ khởi đầu: bày lối vào")
    void offersAtLevelTenOnStartingFaction() {
        controller.onCharacterState(FactionController.MIN_LEVEL, FactionController.STARTING_FACTION);

        assertThat(controller.canOffer()).isTrue();
    }

    @Test
    @DisplayName("ADR-066: đã đổi hệ vẫn mở được cửa sổ (đổi lại), nhưng không nhắc lại lời mời")
    void keepsEntranceAfterLeavingTheStartingFactionButStopsAnnouncing() {
        controller.onCharacterState(30, Faction.FACTION_THUY);

        assertThat(controller.canOffer()).isTrue();
        assertThat(controller.shouldAnnounce()).isFalse();
    }

    @Test
    @DisplayName("Lên cấp giữa trận mở được lối vào — ExperienceChangedEvent là gói duy nhất báo cấp")
    void levelUpOpensTheEntrance() {
        controller.onCharacterState(9, Faction.FACTION_HOA);
        assertThat(controller.canOffer()).isFalse();

        controller.onLevel(10);

        assertThat(controller.canOffer()).isTrue();
        assertThat(controller.level()).isEqualTo(10);
        assertThat(controller.faction())
                .as("onLevel chỉ đổi cấp, không được đụng vào hệ phái")
                .isEqualTo(Faction.FACTION_HOA);
    }

    @Test
    @DisplayName("Server xác nhận xong thì hệ phái đổi, và thôi nhắc lời mời")
    void confirmedSelectionAdoptsTheNewFaction() {
        controller.onCharacterState(12, Faction.FACTION_HOA);
        assertThat(controller.shouldAnnounce()).isTrue();

        controller.onSelectionConfirmed(Faction.FACTION_LOI);

        assertThat(controller.faction()).isEqualTo(Faction.FACTION_LOI);
        assertThat(controller.shouldAnnounce()).isFalse();
    }

    @Test
    @DisplayName("ADR-066: cả năm hệ chọn được (đổi về Hỏa cũng được), đúng thứ tự enum của character.proto")
    void selectableIsEveryFaction() {
        assertThat(FactionController.SELECTABLE)
                .containsExactly(
                        Faction.FACTION_HOA,
                        Faction.FACTION_THUY,
                        Faction.FACTION_PHONG,
                        Faction.FACTION_LOI,
                        Faction.FACTION_THO);
    }

    @Test
    @DisplayName("Tên hiển thị dùng lại khoá i18n của màn hình chọn nhân vật")
    void nameKeysReuseTheExistingBundleEntries() {
        assertThat(FactionController.nameKey(Faction.FACTION_HOA)).isEqualTo("faction.hoa");
        assertThat(FactionController.nameKey(Faction.FACTION_THUY)).isEqualTo("faction.thuy");
        assertThat(FactionController.nameKey(Faction.FACTION_PHONG)).isEqualTo("faction.phong");
        assertThat(FactionController.nameKey(Faction.FACTION_LOI)).isEqualTo("faction.loi");
        assertThat(FactionController.nameKey(Faction.FACTION_THO)).isEqualTo("faction.tho");
    }

    @Test
    @DisplayName("Hệ phái client không nhận ra rơi về khoá 'chưa chọn' thay vì nổ")
    void unknownFactionFallsBackToUnspecifiedKey() {
        assertThat(FactionController.nameKey(Faction.FACTION_UNSPECIFIED)).isEqualTo("faction.unspecified");
        assertThat(FactionController.nameKey(Faction.UNRECOGNIZED)).isEqualTo("faction.unspecified");
    }

    @Test
    @DisplayName("error_key của server dùng THẲNG làm khoá i18n — không dịch tên lại")
    void serverErrorKeyIsUsedVerbatim() {
        assertThat(FactionController.errorMessageKey("error.level_too_low")).isEqualTo("error.level_too_low");
        assertThat(FactionController.errorMessageKey("error.invalid_faction")).isEqualTo("error.invalid_faction");
    }

    @Test
    @DisplayName("error_key rỗng/null rơi về câu 'không hiểu' thay vì im lặng")
    void blankErrorKeyFallsBack() {
        assertThat(FactionController.errorMessageKey("")).isEqualTo("faction.error.unknown");
        assertThat(FactionController.errorMessageKey("   ")).isEqualTo("faction.error.unknown");
        assertThat(FactionController.errorMessageKey(null)).isEqualTo("faction.error.unknown");
    }

    @Test
    @DisplayName("clear() trả về trạng thái đầu — không còn bày lối vào")
    void clearResetsState() {
        controller.onCharacterState(30, Faction.FACTION_HOA);

        controller.clear();

        assertThat(controller.level()).isZero();
        assertThat(controller.faction()).isEqualTo(Faction.FACTION_UNSPECIFIED);
        assertThat(controller.canOffer()).isFalse();
    }
}
