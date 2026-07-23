package dev.kage.dialogue.domain;

import java.util.List;
import java.util.Optional;

/** Cổng đọc dữ liệu tĩnh {@code npc_template} — PHÁT ĐỌC THUẦN, không ghi (config nội dung NPC). */
public interface NpcTemplatePort {

    /** {@code Optional.empty()} nếu {@code npcCode} không tồn tại. */
    Optional<String> findDialogueScriptRefByCode(String npcCode);

    /**
     * Toàn bộ {@code dialogue_script_ref} đang được tham chiếu (bỏ qua NULL) — dùng cho validate
     * script lúc khởi động (SCRIPTING.md §8), KHÔNG dùng trong gameplay. {@code default} rỗng để
     * Fake/lambda hiện có (chỉ implement {@link #findDialogueScriptRefByCode}) không bị vỡ
     * compile — chỉ adapter Postgres thật cần ghi đè phương thức này.
     */
    default List<String> listAllDialogueScriptRefs() {
        return List.of();
    }
}
