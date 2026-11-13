package dev.kage.client.core.ui.hud;

import java.util.function.LongSupplier;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageSkin;

/**
 * Hai dòng chữ nhỏ góc dưới trái (lô 50): đồng hồ phiên chơi (ADR-332, đọc mỗi khung nhưng chỉ đổi chữ khi sang giây
 * mới, không cấp phát) và mục tiêu cấp độ (ADR-334, chữ do coordinator dựng sẵn; rỗng = ẩn).
 */
final class HudInfoLines {

    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SECONDS_PER_HOUR = 3_600L;
    private static final int TWO_DIGITS = 10;

    private final I18NBundle i18n;
    private final Table group = new Table();
    private final Label clockLabel;
    private final Label goalLabel;
    private final StringBuilder clockText = new StringBuilder();
    private LongSupplier sessionSeconds = () -> 0L;
    private long shownSeconds = -1L;

    HudInfoLines(KageSkin skin, I18NBundle i18n) {
        this.i18n = i18n;
        this.clockLabel = new Label("", skin.statLabelStyle);
        this.goalLabel = new Label("", skin.statLabelStyle);
        clockLabel.setVisible(false);
        goalLabel.setVisible(false);
        group.add(goalLabel).left().row();
        group.add(clockLabel).left().row();
    }

    Table group() {
        return group;
    }

    void useSessionSeconds(LongSupplier seconds) {
        this.sessionSeconds = seconds;
        shownSeconds = -1L;
    }

    void setClockVisible(boolean visible) {
        clockLabel.setVisible(visible);
        shownSeconds = -1L;
    }

    void setGoal(String text) {
        goalLabel.setText(text);
        goalLabel.setVisible(!text.isEmpty());
    }

    /** Mỗi khung: chỉ dựng lại chữ khi số giây đổi. */
    void update() {
        if (!clockLabel.isVisible()) {
            return;
        }
        long seconds = Math.max(0L, sessionSeconds.getAsLong());
        if (seconds == shownSeconds) {
            return;
        }
        shownSeconds = seconds;
        clockText.setLength(0);
        clockText.append(i18n.get("hud.session_clock")).append(' ');
        appendClock(clockText, seconds);
        clockLabel.setText(clockText);
    }

    /** {@code H:MM:SS} vào bộ đệm có sẵn (không tạo chuỗi). Tĩnh để test. */
    static void appendClock(StringBuilder out, long totalSeconds) {
        long minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE;
        long seconds = totalSeconds % SECONDS_PER_MINUTE;
        out.append(totalSeconds / SECONDS_PER_HOUR).append(':');
        if (minutes < TWO_DIGITS) {
            out.append('0');
        }
        out.append(minutes).append(':');
        if (seconds < TWO_DIGITS) {
            out.append('0');
        }
        out.append(seconds);
    }
}
