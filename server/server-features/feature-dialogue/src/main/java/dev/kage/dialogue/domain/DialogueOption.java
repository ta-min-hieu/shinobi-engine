package dev.kage.dialogue.domain;

/**
 * 1 lựa chọn trong {@link DialogueNode} — {@code action} là {@code nodeId} kế tiếp (điều hướng tự
 * do trong script), 1 trong 2 hằng số đặc biệt {@link #ACTION_OPEN_SHOP}/{@link #ACTION_END}, HOẶC
 * bắt đầu bằng {@link #ACTION_ACCEPT_QUEST_PREFIX} (Phase 6 lát cắt 3 — nhận quest, phần còn lại
 * của chuỗi là {@code questCode}). Client CHỈ gửi lại {@code optionIndex} khi chọn (không gửi
 * {@code action}) — server tự tra lại action thật từ script theo index, không tin giá trị client
 * tự khai (Điều 24).
 */
public record DialogueOption(String labelKey, String action) {

    public static final String ACTION_OPEN_SHOP = "open_shop";

    /** Mở kho đồ tông môn (ADR-061) — gateway gửi danh sách kho, như {@link #ACTION_OPEN_SHOP}. */
    public static final String ACTION_OPEN_STORAGE = "open_storage";

    /** Mở lò đan (ADR-079) — gateway gửi danh sách công thức, như {@link #ACTION_OPEN_STORAGE}. */
    public static final String ACTION_OPEN_CRAFTING = "open_crafting";

    public static final String ACTION_END = "end";
    public static final String ACTION_ACCEPT_QUEST_PREFIX = "accept_quest:";

    public DialogueOption {
        if (labelKey == null || labelKey.isBlank()) {
            throw new IllegalArgumentException("labelKey không được rỗng");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action không được rỗng");
        }
    }
}
