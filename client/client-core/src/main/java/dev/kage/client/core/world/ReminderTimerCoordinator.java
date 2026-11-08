package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.Timer;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Hẹn giờ nhắc (ADR-259) — {@code /hengio <phút> <nội dung>} (hoặc {@code /remind}): đến giờ hiện một dòng nhắc trong
 * chat; {@code /hengio} liệt kê; {@code /hengio huy} huỷ hết. Tối đa {@value #MAX_TIMERS} lời nhắc, 1–{@value
 * #MAX_MINUTES} phút; chỉ sống trong phiên chơi (thoát game là mất). {@code Timer} của libGDX chạy việc trên luồng vẽ.
 */
final class ReminderTimerCoordinator implements WindowFlow {

    static final int MAX_TIMERS = 5;
    static final int MAX_MINUTES = 600;
    private static final float SECONDS_PER_MINUTE = 60f;

    /** Một lời nhắc đã hẹn. */
    record Parsed(int minutes, String text) {}

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final List<Timer.Task> tasks = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();

    ReminderTimerCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** {@code <phút> <nội dung>} ⇒ lời nhắc; sai dạng / ngoài khoảng ⇒ rỗng. Tĩnh để test. */
    static Optional<Parsed> parse(String argument) {
        String[] parts = argument.strip().split("\\s+", 2);
        if (parts.length < 2 || !parts[0].matches("[0-9]{1,3}") || parts[1].isBlank()) {
            return Optional.empty();
        }
        int minutes = Integer.parseInt(parts[0]);
        return minutes >= 1 && minutes <= MAX_MINUTES
                ? Optional.of(new Parsed(minutes, parts[1].strip()))
                : Optional.empty();
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/hengio".equals(command) && !"/remind".equals(command)) {
            return false;
        }
        String argument = words.length > 1 ? words[1].strip() : "";
        if (argument.isEmpty()) {
            list();
        } else if ("huy".equalsIgnoreCase(argument) || "cancel".equalsIgnoreCase(argument)) {
            tasks.forEach(Timer.Task::cancel);
            tasks.clear();
            labels.clear();
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "remind.cleared"));
        } else {
            schedule(argument);
        }
        return true;
    }

    private void schedule(String argument) {
        Optional<Parsed> parsed = parse(argument);
        if (parsed.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "remind.usage"));
            return;
        }
        if (tasks.size() >= MAX_TIMERS) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "remind.full"));
            return;
        }
        String text = parsed.get().text();
        Timer.Task[] holder = new Timer.Task[1];
        holder[0] = Timer.schedule(
                new Timer.Task() {
                    @Override
                    public void run() {
                        int index = tasks.indexOf(holder[0]);
                        if (index >= 0) {
                            tasks.remove(index);
                            labels.remove(index);
                        }
                        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "remind.fire", text));
                    }
                },
                parsed.get().minutes() * SECONDS_PER_MINUTE);
        tasks.add(holder[0]);
        labels.add(text);
        chat.showSystemNotice(
                KageI18n.formatOrKey(i18n, "remind.set", parsed.get().minutes(), text));
    }

    private void list() {
        chat.showSystemNotice(
                labels.isEmpty()
                        ? KageI18n.textOrKey(i18n, "remind.none")
                        : KageI18n.formatOrKey(i18n, "remind.list", String.join("; ", labels)));
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }

    @Override
    public void reset() {
        tasks.forEach(Timer.Task::cancel);
        tasks.clear();
        labels.clear();
    }
}
