package dev.kage.dialogue.domain;

import java.util.Optional;

/** Cổng đọc dữ liệu tĩnh {@code npc_template} — PHÁT ĐỌC THUẦN, không ghi (config nội dung NPC). */
public interface NpcTemplatePort {

    /** {@code Optional.empty()} nếu {@code npcCode} không tồn tại. */
    Optional<String> findDialogueScriptRefByCode(String npcCode);
}
