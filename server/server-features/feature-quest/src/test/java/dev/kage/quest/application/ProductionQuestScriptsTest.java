package dev.kage.quest.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Bất biến CẤU TRÚC mà MỌI script quest sản phẩm phải giữ, chạy trên TỪNG file thật trong
 * {@code scripts/quest/} (2026-10-11) — cùng vai với {@code ProductionDialogueTreesTest} bên hội thoại.
 *
 * <p>Tầng chạy script cố ý dễ dãi ở ba chỗ, và cả ba đều hỏng IM LẶNG khi script viết sai:
 *
 * <ul>
 *   <li>{@code progress_json} đục với Java: {@code on_accept} không lưu tiến độ (hoặc lưu thứ không
 *       phải JSON) vẫn tạo một quest {@code IN_PROGRESS}, rồi {@code JSON.parse} trong {@code on_event}
 *       lỗi ở MỌI sự kiện về sau — quest không bao giờ xong, cũng không nhận lại được;
 *   <li>{@code give_exp}/{@code give_item} gọi ở {@code on_accept}/{@code on_event} bị bỏ qua: thưởng
 *       chỉ được trao ở {@code on_claim};
 *   <li>{@code complete()}/{@code save_progress} gọi ở {@code on_claim} cũng bị bỏ qua.
 * </ul>
 *
 * <p>Đổi các chỗ đó thành lỗi ồn ở tầng chạy script là quyết định của chủ dự án (docs/README.md dòng
 * 77b/77c). Lớp này không đổi hành vi nào: nó chỉ chặn một script SẢN PHẨM dính các lỗi trên lọt qua
 * build. Không chạm Postgres — vẫn chạy khi máy không bật Docker.
 *
 * <p>Tự dò file nên thêm quest mới không phải sửa gì ở đây.
 */
class ProductionQuestScriptsTest {

    private static final String SCRIPT_DIRECTORY = "scripts/quest";
    private static final String TEST_FIXTURE_PREFIX = "test_fixture";
    /** Loại sự kiện không script nào nhận — mọi {@code on_event} phải đọc được tiến độ rồi bỏ qua nó. */
    private static final Map<String, Object> UNRELATED_EVENT = Map.of("type", "it_unrelated_event");

    private final QuestScriptRunner runner = new QuestScriptRunner();

    static Stream<String> productionScriptRefs() {
        List<String> refs = new ArrayList<>();
        try {
            for (URL directory : Collections.list(
                    ProductionQuestScriptsTest.class.getClassLoader().getResources(SCRIPT_DIRECTORY))) {
                try (Stream<Path> files = Files.list(Path.of(directory.toURI()))) {
                    files.map(file -> Objects.toString(file.getFileName(), ""))
                            .filter(name -> name.endsWith(".js") && !name.startsWith(TEST_FIXTURE_PREFIX))
                            .forEach(name -> refs.add("/" + SCRIPT_DIRECTORY + "/" + name));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Không liệt kê được " + SCRIPT_DIRECTORY, e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Đường dẫn script không hợp lệ", e);
        }
        Collections.sort(refs);
        return refs.stream();
    }

    /** Phép dò trả rỗng thì mọi test tham số bên dưới "xanh" mà không kiểm gì — chặn ngay ở đây. */
    @Test
    void shouldDiscoverTheProductionScripts() {
        assertThat(productionScriptRefs())
                .contains("/scripts/quest/kill_training_dummies.js", "/scripts/quest/foundation_pill_run.js")
                .noneMatch(ref -> ref.contains(TEST_FIXTURE_PREFIX));
    }

    @ParameterizedTest(name = "[{0}] on_accept lưu tiến độ ban đầu và không làm gì khác")
    @MethodSource("productionScriptRefs")
    void onAcceptSavesTheInitialProgressAndNothingElse(String scriptRef) {
        QuestScriptRunner.HookEffects accepted = runner.runAccept(scriptRef);

        assertThat(accepted.scriptFailed()).as("on_accept ném lỗi").isFalse();
        assertThat(accepted.progressJson())
                .as("on_accept phải gọi ctx.save_progress — thiếu nó thì on_event không đọc được tiến độ")
                .isNotBlank();
        assertThat(accepted.completed()).as("quest xong ngay lúc nhận").isFalse();
        assertNoReward(accepted, "on_accept");
        assertThat(accepted.itemTakes()).isEmpty();
    }

    @ParameterizedTest(name = "[{0}] on_event đọc được tiến độ của on_accept và bỏ qua sự kiện lạ")
    @MethodSource("productionScriptRefs")
    void onEventReadsTheAcceptedProgressAndIgnoresAnUnrelatedEvent(String scriptRef) {
        String acceptedProgress = runner.runAccept(scriptRef).progressJson();

        QuestScriptRunner.HookEffects afterEvent = runner.runEvent(scriptRef, acceptedProgress, UNRELATED_EVENT);

        assertThat(afterEvent.scriptFailed())
                .as("on_event lỗi trên chính tiến độ on_accept vừa lưu — quest này không bao giờ xong được")
                .isFalse();
        assertThat(afterEvent.completed()).as("sự kiện lạ làm xong quest").isFalse();
        assertThat(afterEvent.itemTakes()).as("sự kiện lạ đòi lấy đồ").isEmpty();
        assertNoReward(afterEvent, "on_event");
    }

    @ParameterizedTest(name = "[{0}] on_claim chỉ trao thưởng")
    @MethodSource("productionScriptRefs")
    void onClaimOnlyGrantsRewards(String scriptRef) {
        String acceptedProgress = runner.runAccept(scriptRef).progressJson();

        QuestScriptRunner.HookEffects claimed = runner.runClaim(scriptRef, acceptedProgress);

        assertThat(claimed.scriptFailed()).as("on_claim ném lỗi").isFalse();
        assertThat(claimed.completed())
                .as("ctx.complete() ở on_claim bị bỏ qua im lặng")
                .isFalse();
        assertThat(claimed.progressJson())
                .as("ctx.save_progress ở on_claim bị bỏ qua im lặng")
                .isEqualTo(acceptedProgress);
        assertThat(claimed.itemTakes()).isEmpty();
    }

    private static void assertNoReward(QuestScriptRunner.HookEffects effects, String hook) {
        assertThat(effects.expGranted())
                .as("ctx.give_exp ở %s bị bỏ qua im lặng — thưởng chỉ trao ở on_claim", hook)
                .isZero();
        assertThat(effects.itemGrants())
                .as("ctx.give_item ở %s bị bỏ qua im lặng — thưởng chỉ trao ở on_claim", hook)
                .isEmpty();
    }
}
