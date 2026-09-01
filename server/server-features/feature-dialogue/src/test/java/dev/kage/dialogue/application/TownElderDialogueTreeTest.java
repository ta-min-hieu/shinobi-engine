package dev.kage.dialogue.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.dialogue.domain.DialogueNode;
import dev.kage.dialogue.domain.DialogueOption;

/**
 * Kiểm chính CÂY HỘI THOẠI SẢN PHẨM {@code town_elder.js} — khác
 * {@link GetDialogueNodeUseCaseTest} vốn cố ý chỉ dùng fixture test.
 *
 * <p><b>Vì sao cần lớp này.</b> Trước đợt re-theme tu tiên (ADR-038),
 * script sản phẩm chỉ được kiểm ở MỘT chỗ duy nhất: {@code DialogueIntegrationTest} bên
 * {@code server-database}, và test đó cần Postgres qua Testcontainers. Máy không bật Docker thì nó
 * bị BỎ QUA trong im lặng còn build vẫn xanh — nghĩa là nội dung hội thoại có thể hỏng mà không ai
 * biết. Cây này không cần DB để đọc: {@code NpcTemplatePort} chỉ trả về một đường dẫn classpath.
 *
 * <p><b>Thứ được gác.</b> Ngoài nội dung, hai bất biến cấu trúc mà chính javadoc của
 * {@code DialoguePanel} gọi tên là bẫy có thật:
 *
 * <ul>
 *   <li><b>Không node nào cụt.</b> Mọi lối ra khỏi khung hội thoại đều là một lựa chọn trong cây
 *       này; một node quên nhánh {@code end} sẽ NHỐT người chơi trong khung thoại. Rủi ro đó tăng
 *       thẳng theo số node, mà đợt này vừa thêm ba node arc 1.
 *   <li><b>Không action nào trỏ vào hư không.</b> Gõ sai một mã node trong script là một lỗi câm:
 *       không compiler nào bắt, người chơi bấm vào thì hội thoại đứng im.
 * </ul>
 */
class TownElderDialogueTreeTest {

    /** Đường dẫn classpath THẬT của script sản phẩm — cùng chuỗi mà migration V10 ghi vào DB. */
    private static final String PRODUCTION_SCRIPT_REF = "/scripts/dialogue/town_elder.js";

    private static final String NPC_CODE = "town_elder";

    private final GetDialogueNodeUseCase useCase = new GetDialogueNodeUseCase(
            npcCode -> NPC_CODE.equals(npcCode) ? Optional.of(PRODUCTION_SCRIPT_REF) : Optional.empty());

    @Test
    @DisplayName("Node start giữ ĐÚNG 4 lựa chọn theo đúng thứ tự — option_index là VỊ TRÍ")
    void startNodeKeepsItsFourOptionsInOrder() {
        DialogueNode start = node(DialogueNode.START_NODE_ID);

        // Chèn/xoá/đảo một lựa chọn ở đây là đổi ý nghĩa của mọi option_index phía sau nó
        // (dialogue.proto gửi VỊ TRÍ chứ không gửi action), nên thứ tự được ghim bằng test.
        assertThat(start.options())
                .extracting(DialogueOption::action)
                .containsExactly("about_town", "accept_quest:kill_training_dummies", "open_shop", "end");
    }

    @Test
    @DisplayName("Arc 1 mọc xuống dưới: hỏi về tông môn dẫn tiếp được sang cảnh giới và thân thế trưởng lão")
    void outerSectBranchLeadsToTheArcOneContent() {
        DialogueNode aboutSect = node("about_town");

        assertThat(aboutSect.textKey()).isEqualTo("dialogue.town_elder.about_town");
        assertThat(aboutSect.options())
                .extracting(DialogueOption::action)
                .containsExactly("about_realm", "about_elder", "end");
        assertThat(node("about_realm").textKey()).isEqualTo("dialogue.town_elder.about_realm");
        assertThat(node("about_elder").textKey()).isEqualTo("dialogue.town_elder.about_elder");
    }

    @Test
    @DisplayName("Mọi action điều hướng đều trỏ tới một node CÓ THẬT — mã node gõ sai là lỗi câm")
    void everyNavigationActionResolvesToARealNode() {
        for (DialogueNode visited : reachableNodes()) {
            for (DialogueOption option : visited.options()) {
                if (isNavigation(option)) {
                    assertThat(useCase.execute(NPC_CODE, option.action()))
                            .as(
                                    "node \"%s\" có lựa chọn \"%s\" trỏ tới node \"%s\" không tồn tại"
                                            + " — người chơi bấm vào thì hội thoại đứng im",
                                    visited.nodeId(), option.labelKey(), option.action())
                            .isPresent();
                }
            }
        }
    }

    @Test
    @DisplayName("Không node nào NHỐT người chơi — mọi node đều có ít nhất một lối thoát")
    void everyNodeOffersAWayOut() {
        for (DialogueNode visited : reachableNodes()) {
            assertThat(visited.options())
                    .as("node \"%s\" không có lựa chọn nào kết thúc hội thoại", visited.nodeId())
                    .anyMatch(option -> DialogueOption.ACTION_END.equals(option.action()));
        }
    }

    @Test
    @DisplayName("Nhịp hé lộ arc 1: chưa node nào được chạm tới bí mật của arc 3 và arc 5")
    void arcOneDoesNotLeakLaterArcs() {
        // STORY.md §8 quy tắc 2 là luật NỘI DUNG, nên chỉ khoá i18n mới kiểm được ở tầng này —
        // chữ thật nằm bên bundle client. Đủ để bắt việc thêm nhầm một node của arc sau vào cây
        // NPC mở màn, đó mới là lỗi thật sự dễ xảy ra.
        List<String> forbidden = List.of("thien_mon", "dai_kiep", "jade_shard", "betrayal");
        for (DialogueNode visited : reachableNodes()) {
            assertThat(forbidden)
                    .as("node \"%s\" (khoá %s) chạm tới nội dung của arc sau", visited.nodeId(), visited.textKey())
                    .noneMatch(secret -> visited.textKey().contains(secret));
        }
    }

    /**
     * Duyệt rộng toàn cây từ {@code start}, đi theo mọi action điều hướng.
     *
     * <p>Node không đọc được thì BỎ QUA thay vì làm hỏng phép duyệt: chính
     * {@link #everyNavigationActionResolvesToARealNode()} mới là chỗ tố cáo nó, và nó nói rõ node
     * nào trỏ đi đâu — hữu ích hơn nhiều một lỗi chung chung phát ra từ hàm duyệt.
     */
    private List<DialogueNode> reachableNodes() {
        List<DialogueNode> visited = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(DialogueNode.START_NODE_ID);
        seen.add(DialogueNode.START_NODE_ID);
        while (!queue.isEmpty()) {
            String nodeId = queue.removeFirst();
            Optional<DialogueNode> current = useCase.execute(NPC_CODE, nodeId);
            if (current.isEmpty()) {
                continue;
            }
            visited.add(current.get());
            for (DialogueOption option : current.get().options()) {
                if (isNavigation(option) && seen.add(option.action())) {
                    queue.addLast(option.action());
                }
            }
        }
        assertThat(visited)
                .as("không đọc nổi cả node start của %s", PRODUCTION_SCRIPT_REF)
                .isNotEmpty();
        return visited;
    }

    /** Action nào là "đi tới node khác" — ba dạng còn lại do gateway xử lý, không phải node. */
    private static boolean isNavigation(DialogueOption option) {
        return !DialogueOption.ACTION_END.equals(option.action())
                && !DialogueOption.ACTION_OPEN_SHOP.equals(option.action())
                && !option.action().startsWith(DialogueOption.ACTION_ACCEPT_QUEST_PREFIX);
    }

    private DialogueNode node(String nodeId) {
        Optional<DialogueNode> result = useCase.execute(NPC_CODE, nodeId);
        assertThat(result)
                .as("không đọc được node \"%s\" từ %s", nodeId, PRODUCTION_SCRIPT_REF)
                .isPresent();
        return result.get();
    }
}
