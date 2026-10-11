package dev.kage.quest.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Hợp đồng "hàm nào ở hook nào" của {@code ctx.*} (quyết định chủ dự án 2026-10-11, docs/README.md
 * dòng 77(b)/(c)).
 *
 * <p>Trước đó cả bốn hàm có ở mọi hook, và gọi sai chỗ bị bỏ qua im lặng: thưởng gọi ở
 * {@code on_accept}/{@code on_event} không bao giờ tới tay người chơi, {@code complete()} ở
 * {@code on_claim} không đổi gì. Tệ nhất là {@code on_accept} quên lưu tiến độ: quest thành
 * {@code IN_PROGRESS} rồi kẹt vĩnh viễn. Nay mọi ca đó là lỗi script — use case bỏ hết hiệu ứng của một
 * script lỗi, nên không gì dở dang lọt xuống DB.
 *
 * <p>Script SẢN PHẨM được gác riêng bởi {@link ProductionQuestScriptsTest}; lớp này gác chính cơ chế.
 */
class QuestScriptRunnerHookContractTest {

    private static final String WRONG_HOOK_SCRIPT_REF = "/scripts/quest/test_fixture_wrong_hook.js";
    private static final Map<String, Object> ANY_EVENT = Map.of("type", "monster_killed", "monster_code", "x");

    private final QuestScriptRunner runner = new QuestScriptRunner();

    @ParameterizedTest(name = "on_event: ctx.{0} không tồn tại ⇒ lỗi script")
    @ValueSource(strings = {"give_exp", "give_item"})
    void shouldFailTheScript_whenARewardIsGrantedDuringAnEvent(String caseName) {
        QuestScriptRunner.HookEffects effects =
                runner.runEvent(WRONG_HOOK_SCRIPT_REF, progressFor(caseName), ANY_EVENT);

        assertThat(effects.scriptFailed()).isTrue();
    }

    @ParameterizedTest(name = "on_claim: ctx.{0} không tồn tại ⇒ lỗi script")
    @ValueSource(strings = {"complete", "save_progress", "take_item"})
    void shouldFailTheScript_whenAClaimTouchesProgress(String caseName) {
        QuestScriptRunner.HookEffects effects = runner.runClaim(WRONG_HOOK_SCRIPT_REF, progressFor(caseName));

        assertThat(effects.scriptFailed()).isTrue();
    }

    /** Đối chứng: cùng fixture, nhánh không gọi gì sai chỗ thì chạy sạch ở cả hai hook. */
    @Test
    void shouldRunCleanly_whenEveryCallIsInItsOwnHook() {
        QuestScriptRunner.HookEffects afterEvent =
                runner.runEvent(WRONG_HOOK_SCRIPT_REF, progressFor("none"), ANY_EVENT);
        QuestScriptRunner.HookEffects afterClaim = runner.runClaim(WRONG_HOOK_SCRIPT_REF, progressFor("none"));

        assertThat(afterEvent.scriptFailed()).isFalse();
        assertThat(afterEvent.progressJson()).contains("seen");
        assertThat(afterClaim.scriptFailed()).isFalse();
        assertThat(afterClaim.expGranted()).isEqualTo(10);
    }

    @Test
    void shouldFailTheAccept_whenItGrantsAReward() {
        assertThat(runner.runAccept("/scripts/quest/test_fixture_accept_gives_reward.js")
                        .scriptFailed())
                .isTrue();
    }

    @Test
    void shouldFailTheAccept_whenItSavesNoProgress() {
        assertThat(runner.runAccept("/scripts/quest/test_fixture_accept_saves_nothing.js")
                        .scriptFailed())
                .isTrue();
    }

    @Test
    void shouldFailTheAccept_whenTheSavedProgressIsNotJson() {
        assertThat(runner.runAccept("/scripts/quest/test_fixture_accept_saves_garbage.js")
                        .scriptFailed())
                .isTrue();
    }

    /** Đối chứng: {@code on_accept} lưu JSON hợp lệ thì việc nhận quest thành công. */
    @Test
    void shouldAccept_whenTheInitialProgressIsValidJson() {
        QuestScriptRunner.HookEffects effects = runner.runAccept(WRONG_HOOK_SCRIPT_REF);

        assertThat(effects.scriptFailed()).isFalse();
        assertThat(effects.progressJson()).isEqualTo("{}");
    }

    private static String progressFor(String caseName) {
        return "{\"case\":\"" + caseName + "\"}";
    }
}
