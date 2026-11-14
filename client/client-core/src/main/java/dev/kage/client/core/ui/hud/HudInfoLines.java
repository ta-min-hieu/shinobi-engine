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
    private static final int MINUTES_PER_HOUR = 60;

    private final I18NBundle i18n;
    private final Table group = new Table();
    private final Label clockLabel;
    private final Label goalLabel;
    private final Label resetLabel; // ADR-356
    private final Label localClockLabel; // ADR-372
    private final Label killsLabel; // ADR-374
    private final StringBuilder localClockText = new StringBuilder();
    private final StringBuilder killsText = new StringBuilder();
    private java.util.function.IntSupplier minuteOfDay = () -> 0;
    private java.util.function.IntSupplier kills = () -> 0;
    private int shownMinute = -1;
    private int shownKills = -1;
    private final StringBuilder clockText = new StringBuilder();
    private final StringBuilder resetText = new StringBuilder();
    private LongSupplier sessionSeconds = () -> 0L;
    private LongSupplier secondsToReset = () -> 0L;
    private long shownSeconds = -1L;
    private long shownReset = -1L;
    private final KageSkin skin;
    private final java.util.List<HudCounterLine> counters = new java.util.ArrayList<>(); // lô 58+

    HudInfoLines(KageSkin skin, I18NBundle i18n) {
        this.i18n = i18n;
        this.skin = skin;
        this.clockLabel = new Label("", skin.statLabelStyle);
        this.goalLabel = new Label("", skin.statLabelStyle);
        this.resetLabel = new Label("", skin.statLabelStyle);
        this.localClockLabel = new Label("", skin.statLabelStyle);
        this.killsLabel = new Label("", skin.statLabelStyle);
        localClockLabel.setVisible(false);
        killsLabel.setVisible(false);
        clockLabel.setVisible(false);
        goalLabel.setVisible(false);
        resetLabel.setVisible(false);
        group.add(goalLabel).left().row();
        group.add(resetLabel).left().row();
        group.add(localClockLabel).left().row();
        group.add(killsLabel).left().row();
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

    void useResetCountdown(LongSupplier seconds) {
        this.secondsToReset = seconds;
        shownReset = -1L;
    }

    void setResetVisible(boolean visible) {
        resetLabel.setVisible(visible);
        shownReset = -1L;
    }

    void useLocalClock(java.util.function.IntSupplier minutesOfDay) {
        this.minuteOfDay = minutesOfDay;
        shownMinute = -1;
    }

    void setLocalClockVisible(boolean visible) {
        localClockLabel.setVisible(visible);
        shownMinute = -1;
    }

    void useKills(java.util.function.IntSupplier killCount) {
        this.kills = killCount;
        shownKills = -1;
    }

    void setKillsVisible(boolean visible) {
        killsLabel.setVisible(visible);
        shownKills = -1;
    }

    /** Lô 58+ — thêm một dòng số (ẩn sẵn); trả công tắc hiện/ẩn của dòng đó. */
    java.util.function.Consumer<Boolean> addCounter(String prefixKey, LongSupplier value, boolean clock) {
        HudCounterLine line = new HudCounterLine(
                new Label("", skin.statLabelStyle),
                i18n.get(prefixKey),
                value,
                clock ? HudCounterLine.Format.CLOCK : HudCounterLine.Format.NUMBER);
        counters.add(line);
        group.add(line.label()).left().row();
        return line::setVisible;
    }

    void setGoal(String text) {
        goalLabel.setText(text);
        goalLabel.setVisible(!text.isEmpty());
    }

    /** Mỗi khung: chỉ dựng lại chữ khi số giây đổi. */
    void update() {
        updateReset();
        updateLocalClock();
        updateKills();
        for (int index = 0; index < counters.size(); index++) {
            counters.get(index).update();
        }
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

    /** ADR-372 — giờ máy HH:MM; chỉ đổi chữ khi sang phút mới. */
    private void updateLocalClock() {
        if (!localClockLabel.isVisible()) {
            return;
        }
        int minute = minuteOfDay.getAsInt();
        if (minute == shownMinute) {
            return;
        }
        shownMinute = minute;
        localClockText.setLength(0);
        appendHourMinute(localClockText, minute);
        localClockLabel.setText(localClockText);
    }

    /** ADR-374 — số quái đã hạ; chỉ đổi chữ khi số đổi. */
    private void updateKills() {
        if (!killsLabel.isVisible()) {
            return;
        }
        int count = kills.getAsInt();
        if (count == shownKills) {
            return;
        }
        shownKills = count;
        killsText.setLength(0);
        killsText.append(i18n.get("hud.kills")).append(' ').append(count);
        killsLabel.setText(killsText);
    }

    /** {@code HH:MM} từ số phút trong ngày, vào bộ đệm có sẵn. Tĩnh để test. */
    static void appendHourMinute(StringBuilder out, int minuteOfDay) {
        int hour = minuteOfDay / MINUTES_PER_HOUR;
        int minute = minuteOfDay % MINUTES_PER_HOUR;
        if (hour < TWO_DIGITS) {
            out.append('0');
        }
        out.append(hour).append(':');
        if (minute < TWO_DIGITS) {
            out.append('0');
        }
        out.append(minute);
    }

    /** ADR-356 — đếm ngược tới reset ngày; cùng khuôn đồng hồ phiên (không cấp phát). */
    private void updateReset() {
        if (!resetLabel.isVisible()) {
            return;
        }
        long seconds = Math.max(0L, secondsToReset.getAsLong());
        if (seconds == shownReset) {
            return;
        }
        shownReset = seconds;
        resetText.setLength(0);
        resetText.append(i18n.get("hud.reset_countdown")).append(' ');
        appendClock(resetText, seconds);
        resetLabel.setText(resetText);
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
