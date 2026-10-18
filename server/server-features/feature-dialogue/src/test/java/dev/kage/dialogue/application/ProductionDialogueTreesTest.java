package dev.kage.dialogue.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.kage.dialogue.domain.DialogueNode;
import dev.kage.dialogue.domain.DialogueOption;

/**
 * Ba bất biến CẤU TRÚC mà MỌI cây hội thoại sản phẩm phải giữ, chạy trên TỪNG NPC có thật.
 *
 * <p><b>Vì sao tách khỏi {@link TownElderDialogueTreeTest} (2026-09-09).</b> Ba phép kiểm dưới đây
 * ra đời khi thế giới chỉ có ĐÚNG MỘT NPC, nên chúng nằm chung với các phép kiểm NỘI DUNG của
 * Chu Hoành. NPC thứ hai ({@code senior_sister}, seed V37) làm lộ ra rằng chúng là hai loại test
 * khác nhau: nội dung thì mỗi NPC một khác, còn "không node nào cụt" thì đúng với mọi cây, mãi
 * mãi. Copy-paste chúng sang lớp test thứ hai là dựng sẵn chỗ cho hai bản trôi khỏi nhau — cùng lý
 * do {@code ProcessQuestEventUseCase} được tổng quát hoá thay vì nhân bản.
 *
 * <p>⚠️ THÊM NPC MỚI THÌ THÊM MỘT DÒNG VÀO {@link #ALL_PRODUCTION_NPCS}. Không có cách nào tự dò:
 * cặp (mã NPC, đường dẫn script) là dữ liệu nằm trong bảng {@code npc_template}, mà tầng này cố ý
 * KHÔNG chạm tới Postgres (xem javadoc {@link TownElderDialogueTreeTest} — giá trị của cả nhánh
 * test này là nó vẫn chạy khi máy không bật Docker).
 */
class ProductionDialogueTreesTest {

    /** Mỗi dòng: {@code <npc_code>, <dialogue_script_ref>} — ĐÚNG cặp giá trị mà migration ghi vào DB. */
    private static final String ALL_PRODUCTION_NPCS = """
            town_elder, /scripts/dialogue/town_elder.js
            senior_sister, /scripts/dialogue/senior_sister.js
            waypost_attendant, /scripts/dialogue/waypost_attendant.js
            """;

    @ParameterizedTest(name = "[{0}] mọi action điều hướng trỏ tới một node CÓ THẬT")
    @CsvSource(textBlock = ALL_PRODUCTION_NPCS)
    void everyNavigationActionResolvesToARealNode(String npcCode, String scriptRef) {
        GetDialogueNodeUseCase useCase = useCaseFor(npcCode, scriptRef);
        for (DialogueNode visited : reachableNodes(useCase, npcCode, scriptRef)) {
            for (DialogueOption option : visited.options()) {
                if (isNavigation(option)) {
                    assertThat(useCase.execute(npcCode, option.action()))
                            .as(
                                    "node \"%s\" của %s có lựa chọn \"%s\" trỏ tới node \"%s\" không tồn tại"
                                            + " — người chơi bấm vào thì hội thoại đứng im",
                                    visited.nodeId(), npcCode, option.labelKey(), option.action())
                            .isPresent();
                }
            }
        }
    }

    @ParameterizedTest(name = "[{0}] không node nào NHỐT người chơi")
    @CsvSource(textBlock = ALL_PRODUCTION_NPCS)
    void everyNodeOffersAWayOut(String npcCode, String scriptRef) {
        for (DialogueNode visited : reachableNodes(useCaseFor(npcCode, scriptRef), npcCode, scriptRef)) {
            assertThat(visited.options())
                    .as("node \"%s\" của %s không có lựa chọn nào kết thúc hội thoại", visited.nodeId(), npcCode)
                    .anyMatch(option -> DialogueOption.ACTION_END.equals(option.action()));
        }
    }

    /**
     * STORY.md §8 quy tắc 2 — nhịp hé lộ: arc 1 GIEO, arc 3 NGHI, arc 5 LỘ, arc 6 CHỌN. Hai NPC hiện
     * có sống ở arc 1 và arc 2, nên không cây nào được chạm tới bí mật của arc 3 trở đi.
     *
     * <p>Chỉ khoá i18n kiểm được ở tầng này (chữ thật nằm bên bundle client) — đủ để bắt lỗi thật sự
     * dễ xảy ra: thêm nhầm một node của arc sau vào một cây của arc trước.
     */
    @ParameterizedTest(name = "[{0}] chưa cây nào chạm tới bí mật của arc 3 trở đi")
    @CsvSource(textBlock = ALL_PRODUCTION_NPCS)
    void noTreeLeaksLaterArcs(String npcCode, String scriptRef) {
        List<String> forbidden = List.of("thien_mon", "dai_kiep", "jade_shard", "betrayal", "huyet_sa", "ngoc_co");
        for (DialogueNode visited : reachableNodes(useCaseFor(npcCode, scriptRef), npcCode, scriptRef)) {
            assertThat(forbidden)
                    .as(
                            "node \"%s\" của %s (khoá %s) chạm tới nội dung của arc sau",
                            visited.nodeId(), npcCode, visited.textKey())
                    .noneMatch(secret -> visited.textKey().contains(secret));
        }
    }

    private static GetDialogueNodeUseCase useCaseFor(String npcCode, String scriptRef) {
        return new GetDialogueNodeUseCase(
                requested -> npcCode.equals(requested) ? Optional.of(scriptRef) : Optional.empty());
    }

    /**
     * Duyệt rộng toàn cây từ {@code start}, đi theo mọi action điều hướng.
     *
     * <p>Node không đọc được thì BỎ QUA thay vì làm hỏng phép duyệt: chính
     * {@link #everyNavigationActionResolvesToARealNode} mới là chỗ tố cáo nó, và nó nói rõ node nào
     * trỏ đi đâu — hữu ích hơn nhiều một lỗi chung chung phát ra từ hàm duyệt.
     */
    private static List<DialogueNode> reachableNodes(GetDialogueNodeUseCase useCase, String npcCode, String scriptRef) {
        List<DialogueNode> visited = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(DialogueNode.START_NODE_ID);
        seen.add(DialogueNode.START_NODE_ID);
        while (!queue.isEmpty()) {
            String nodeId = queue.removeFirst();
            Optional<DialogueNode> current = useCase.execute(npcCode, nodeId);
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
        assertThat(visited).as("không đọc nổi cả node start của %s", scriptRef).isNotEmpty();
        return visited;
    }

    /** Action nào là "đi tới node khác" — ba dạng còn lại do gateway xử lý, không phải node. */
    private static boolean isNavigation(DialogueOption option) {
        return !DialogueOption.ACTION_END.equals(option.action())
                && !DialogueOption.ACTION_OPEN_SHOP.equals(option.action())
                && !DialogueOption.ACTION_OPEN_STORAGE.equals(option.action())
                && !option.action().startsWith(DialogueOption.ACTION_ACCEPT_QUEST_PREFIX);
    }
}
