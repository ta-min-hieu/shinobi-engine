package dev.kage.dialogue.domain;

/**
 * 1 lựa chọn trong {@link DialogueNode} — {@code action} là {@code nodeId} kế tiếp (điều hướng
 * tự do trong script) HOẶC 1 trong 2 hằng số đặc biệt {@link #ACTION_OPEN_SHOP}/{@link #ACTION_END}.
 * Client CHỈ gửi lại {@code optionIndex} khi chọn (không gửi {@code action}) — server tự tra lại
 * action thật từ script theo index, không tin giá trị client tự khai (Điều 24).
 */
public record DialogueOption(String labelKey, String action) {

    public static final String ACTION_OPEN_SHOP = "open_shop";
    public static final String ACTION_END = "end";

    public DialogueOption {
        if (labelKey == null || labelKey.isBlank()) {
            throw new IllegalArgumentException("labelKey không được rỗng");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action không được rỗng");
        }
    }
}
