package dev.kage.dialogue.domain;

import java.util.List;

/**
 * 1 node hội thoại — nội dung THẬT nằm trong script NPC (Điều 27, GAMEPLAY.md §9.2), record này
 * chỉ là kết quả ĐÃ trích xuất (application layer trích qua {@code ScriptSandbox}, domain không
 * biết GraalJS).
 */
public record DialogueNode(String nodeId, String textKey, List<DialogueOption> options) {

    /** Node vào đầu tiên mọi script dialogue PHẢI có — quy ước, không ép bằng code. */
    public static final String START_NODE_ID = "start";

    public DialogueNode {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId không được rỗng");
        }
        if (textKey == null || textKey.isBlank()) {
            throw new IllegalArgumentException("textKey không được rỗng");
        }
        options = List.copyOf(options);
    }
}
