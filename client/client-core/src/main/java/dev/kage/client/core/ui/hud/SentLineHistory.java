package dev.kage.client.core.ui.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gọi lại tin đã gõ (ADR-262) — nhớ tối đa {@value #MAX_LINES} dòng vừa gửi (mới nhất cuối); {@link #previous()} /
 * {@link #next()} đi lùi / tiến như ↑ / ↓ trong ô chat. Gửi dòng mới ⇒ con trỏ về cuối. Chỉ trong phiên.
 */
public final class SentLineHistory {

    public static final int MAX_LINES = 30;

    private final List<String> lines = new ArrayList<>();
    private int cursor;

    /** Ghi dòng vừa gửi (rỗng hoặc trùng dòng liền trước thì bỏ). */
    public void remember(String line) {
        String trimmed = line.strip();
        if (!trimmed.isEmpty() && (lines.isEmpty() || !lines.getLast().equals(trimmed))) {
            lines.add(trimmed);
            if (lines.size() > MAX_LINES) {
                lines.removeFirst();
            }
        }
        cursor = lines.size();
    }

    /** Dòng trước (↑); đã ở đầu thì giữ dòng đầu; chưa có gì ⇒ rỗng. */
    public Optional<String> previous() {
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        cursor = Math.max(0, cursor - 1);
        return Optional.of(lines.get(cursor));
    }

    /** Dòng sau (↓); qua dòng cuối ⇒ chuỗi rỗng (ô nhập trống như lúc mới mở). */
    public Optional<String> next() {
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        cursor = Math.min(lines.size(), cursor + 1);
        return Optional.of(cursor == lines.size() ? "" : lines.get(cursor));
    }
}
