package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.EnterDungeonOutcome;
import dev.kage.protocol.ExitDungeonOutcome;

/**
 * {@link DungeonController} thuần Java, không libGDX — chạy được thẳng trong JUnit như
 * {@code GuildControllerTest}.
 */
class DungeonControllerTest {

    private final DungeonController controller = new DungeonController();

    @Test
    @DisplayName("Chưa làm gì: không ở trong hầm ngục nào")
    void startsOutsideDungeon() {
        assertThat(controller.inDungeon()).isFalse();
    }

    @Test
    @DisplayName("Mã hầm ngục khớp hằng số hardcode của server (DungeonInstanceRegistry)")
    void usesTheOnlyDungeonCodeTheServerAccepts() {
        assertThat(DungeonController.DUNGEON_CODE).isEqualTo("sample_dungeon");
    }

    @Test
    @DisplayName("OK là đường vào: server nói vào được thì mới coi là đang ở trong")
    void entersOnOk() {
        controller.onEnterOutcome(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK);

        assertThat(controller.inDungeon()).isTrue();
    }

    @Test
    @DisplayName("ALREADY_IN_DUNGEON cũng là lời server xác nhận đang ở trong — đường duy nhất của người bị kéo theo")
    void adoptsInDungeonFromAlreadyInDungeon() {
        controller.onEnterOutcome(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_ALREADY_IN_DUNGEON);

        assertThat(controller.inDungeon()).isTrue();
    }

    @Test
    @DisplayName("Lệnh vào hỏng vì lý do khác thì KHÔNG tự cho là đã vào")
    void staysOutsideOnFailure() {
        controller.onEnterOutcome(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_NOT_IN_PARTY);
        assertThat(controller.inDungeon()).isFalse();

        controller.onEnterOutcome(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_PARTY_MEMBER_OFFLINE);
        assertThat(controller.inDungeon()).isFalse();

        controller.onEnterOutcome(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_UNKNOWN_DUNGEON);
        assertThat(controller.inDungeon()).isFalse();
    }

    @Test
    @DisplayName("Cả hai outcome khi rời đều kết luận 'không còn ở trong hầm ngục'")
    void bothExitOutcomesLeaveTheDungeon() {
        controller.onEnterOutcome(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK);
        controller.onExitOutcome(ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_OK);
        assertThat(controller.inDungeon()).isFalse();

        controller.onEnterOutcome(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK);
        controller.onExitOutcome(ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_NOT_IN_DUNGEON);
        assertThat(controller.inDungeon())
                .as("NOT_IN_DUNGEON nghĩa là màn hình đang hiện sai — phải sửa theo server")
                .isFalse();
    }

    @Test
    @DisplayName("Chỉ OK mới là một cú ĐỔI World — ALREADY_IN_DUNGEON thì không dọn màn hình")
    void onlyOkChangesWorld() {
        assertThat(DungeonController.changesWorld(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK))
                .isTrue();
        assertThat(DungeonController.changesWorld(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_ALREADY_IN_DUNGEON))
                .isFalse();
        assertThat(DungeonController.changesWorld(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_NOT_IN_PARTY))
                .isFalse();
        assertThat(DungeonController.changesWorld(ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_OK))
                .isTrue();
        assertThat(DungeonController.changesWorld(ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_NOT_IN_DUNGEON))
                .isFalse();
    }

    @Test
    @DisplayName("Mọi outcome vào hầm ngục đều có khoá i18n riêng — không nuốt case nào")
    void everyEnterOutcomeHasItsOwnKey() {
        assertThat(DungeonController.enterMessageKey(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK))
                .contains("dungeon.notice.entered");
        assertThat(DungeonController.enterMessageKey(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_NOT_IN_PARTY))
                .contains("dungeon.error.not_in_party");
        assertThat(DungeonController.enterMessageKey(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_PARTY_MEMBER_OFFLINE))
                .contains("dungeon.error.member_offline");
        assertThat(DungeonController.enterMessageKey(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_ALREADY_IN_DUNGEON))
                .contains("dungeon.error.already_in_dungeon");
        assertThat(DungeonController.enterMessageKey(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_UNKNOWN_DUNGEON))
                .contains("dungeon.error.unknown_dungeon");
    }

    @Test
    @DisplayName("Mọi outcome rời hầm ngục đều có khoá i18n riêng")
    void everyExitOutcomeHasItsOwnKey() {
        assertThat(DungeonController.exitMessageKey(ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_OK))
                .contains("dungeon.notice.exited");
        assertThat(DungeonController.exitMessageKey(ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_NOT_IN_DUNGEON))
                .contains("dungeon.error.not_in_dungeon");
    }

    @Test
    @DisplayName("UNSPECIFIED/UNRECOGNIZED không có khoá — chỗ gọi nói thẳng là không hiểu")
    void unknownOutcomesHaveNoKey() {
        assertThat(DungeonController.enterMessageKey(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_UNSPECIFIED))
                .isEmpty();
        assertThat(DungeonController.enterMessageKey(EnterDungeonOutcome.UNRECOGNIZED))
                .isEmpty();
        assertThat(DungeonController.exitMessageKey(ExitDungeonOutcome.EXIT_DUNGEON_OUTCOME_UNSPECIFIED))
                .isEmpty();
        assertThat(DungeonController.exitMessageKey(ExitDungeonOutcome.UNRECOGNIZED))
                .isEmpty();
    }

    @Test
    @DisplayName("clear() trả về trạng thái đầu — ảnh chụp một phiên không sống sót sang phiên sau")
    void clearResetsState() {
        controller.onEnterOutcome(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK);

        controller.clear();

        assertThat(controller.inDungeon()).isFalse();
    }
}
