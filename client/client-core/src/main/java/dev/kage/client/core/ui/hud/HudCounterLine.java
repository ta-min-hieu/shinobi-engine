package dev.kage.client.core.ui.hud;

import java.util.function.LongSupplier;

import com.badlogic.gdx.scenes.scene2d.ui.Label;

/**
 * Một dòng số nhỏ trên HUD (lô 58+): tiền tố cố định + giá trị đọc mỗi khung, chỉ dựng lại chữ khi giá trị đổi (không
 * cấp phát khi đứng yên). Giá trị âm = "không có gì để hiện" ⇒ chữ rỗng (vd. đồng hồ giao chiến lúc không đánh).
 */
final class HudCounterLine {

    /** Cách viết giá trị. */
    enum Format {
        /** Số nguyên như nó là. */
        NUMBER,
        /** {@code H:MM:SS}. */
        CLOCK
    }

    private final Label label;
    private final String prefix;
    private final LongSupplier value;
    private final Format format;
    private final StringBuilder text = new StringBuilder();
    private long shown = Long.MIN_VALUE;

    HudCounterLine(Label label, String prefix, LongSupplier value, Format format) {
        this.label = label;
        this.prefix = prefix;
        this.value = value;
        this.format = format;
        label.setVisible(false);
    }

    Label label() {
        return label;
    }

    void setVisible(boolean visible) {
        label.setVisible(visible);
        shown = Long.MIN_VALUE;
    }

    /** Mỗi khung: chỉ đổi chữ khi giá trị đổi. */
    void update() {
        if (!label.isVisible()) {
            return;
        }
        long current = value.getAsLong();
        if (current == shown) {
            return;
        }
        shown = current;
        text.setLength(0);
        if (current >= 0L) {
            text.append(prefix).append(' ');
            if (format == Format.CLOCK) {
                HudInfoLines.appendClock(text, current);
            } else {
                text.append(current);
            }
        }
        label.setText(text);
    }
}
