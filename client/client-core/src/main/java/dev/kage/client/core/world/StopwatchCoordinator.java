package dev.kage.client.core.world;

import java.util.Locale;
import java.util.function.LongSupplier;

import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Đồng hồ bấm giờ (ADR-301) — {@code /bamgio} (hoặc {@code /stopwatch}): lần đầu bắt đầu, lần sau dừng và in thời gian
 * đã trôi; {@code /bamgio xem} xem mà không dừng. Giờ máy người chơi, chỉ sống trong phiên.
 */
final class StopwatchCoordinator implements WindowFlow {

    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SECONDS_PER_HOUR = 3_600L;

    private final ChatCoordinator chat;
    private final LongSupplier clockMillis;
    private final I18NBundle i18n = KageI18n.load();
    private long startedAtMillis = -1L;

    StopwatchCoordinator(ChatCoordinator chat, LongSupplier clockMillis) {
        this.chat = chat;
        this.clockMillis = clockMillis;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/bamgio".equals(command) && !"/stopwatch".equals(command)) {
            return false;
        }
        String arg = words.length > 1 ? MentionWatch.fold(words[1].strip()) : "";
        long now = clockMillis.getAsLong();
        if (startedAtMillis < 0L) {
            startedAtMillis = now;
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "stopwatch.started"));
            return true;
        }
        String elapsed = format(now - startedAtMillis);
        if ("xem".equals(arg) || "peek".equals(arg)) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "stopwatch.running", elapsed));
            return true;
        }
        startedAtMillis = -1L;
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "stopwatch.stopped", elapsed));
        return true;
    }

    /** {@code H:MM:SS.t} hoặc {@code M:SS.t}. Tĩnh để test. */
    static String format(long elapsedMillis) {
        long millis = Math.max(0L, elapsedMillis);
        long totalSeconds = millis / MILLIS_PER_SECOND;
        long tenths = millis % MILLIS_PER_SECOND / 100L;
        long hours = totalSeconds / SECONDS_PER_HOUR;
        long minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE;
        long seconds = totalSeconds % SECONDS_PER_MINUTE;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d.%d", hours, minutes, seconds, tenths)
                : String.format(Locale.ROOT, "%d:%02d.%d", minutes, seconds, tenths);
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
