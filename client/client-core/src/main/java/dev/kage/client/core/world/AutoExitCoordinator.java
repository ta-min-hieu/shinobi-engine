package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.Timer;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Hẹn giờ tự thoát game (ADR-388) — {@code /henthoat <phút>} (hoặc {@code /autoexit}), 1–{@value #MAX_MINUTES} phút;
 * còn {@value #WARN_SECONDS} giây thì báo trước; {@code /henthoat huy} huỷ; {@code /henthoat} hỏi còn bao lâu. Chỉ một
 * hẹn; hẹn mới thay hẹn cũ. Thoát bằng {@code Gdx.app.exit()} — như bấm đóng cửa sổ.
 */
final class AutoExitCoordinator implements WindowFlow {

    static final int MAX_MINUTES = 600;
    static final int WARN_SECONDS = 60;
    private static final int SECONDS_PER_MINUTE = 60;
    private static final long MILLIS_PER_SECOND = 1_000L;

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private Optional<Timer.Task> exitTask = Optional.empty();
    private Optional<Timer.Task> warnTask = Optional.empty();
    private long exitAtMillis;
    private Runnable exit = () -> {
        if (Gdx.app != null) {
            Gdx.app.exit();
        }
    };

    AutoExitCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Thay hành động thoát (test). */
    void useExit(Runnable action) {
        this.exit = action;
    }

    /** {@code "<phút>"} trong khoảng ⇒ số phút; còn lại rỗng. Tĩnh để test. */
    static Optional<Integer> parseMinutes(String argument) {
        if (!argument.matches("[0-9]{1,3}")) {
            return Optional.empty();
        }
        int minutes = Integer.parseInt(argument);
        return minutes >= 1 && minutes <= MAX_MINUTES ? Optional.of(minutes) : Optional.empty();
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/henthoat".equals(command) && !"/autoexit".equals(command)) {
            return false;
        }
        String argument = words.length > 1 ? words[1].strip() : "";
        if (argument.isEmpty()) {
            status();
        } else if ("huy".equalsIgnoreCase(argument) || "cancel".equalsIgnoreCase(argument)) {
            boolean had = exitTask.isPresent();
            cancel();
            chat.showSystemNotice(KageI18n.textOrKey(i18n, had ? "autoexit.cancelled" : "autoexit.none"));
        } else {
            parseMinutes(argument)
                    .ifPresentOrElse(
                            this::schedule,
                            () -> chat.showSystemNotice(KageI18n.formatOrKey(i18n, "autoexit.usage", MAX_MINUTES)));
        }
        return true;
    }

    private void schedule(int minutes) {
        cancel();
        int seconds = minutes * SECONDS_PER_MINUTE;
        exitAtMillis = System.currentTimeMillis() + seconds * MILLIS_PER_SECOND;
        exitTask = Optional.of(Timer.schedule(task(() -> exit.run()), seconds));
        if (seconds > WARN_SECONDS) {
            warnTask = Optional.of(Timer.schedule(
                    task(() -> chat.showSystemNotice(KageI18n.textOrKey(i18n, "autoexit.warn"))),
                    seconds - (float) WARN_SECONDS));
        }
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "autoexit.set", minutes));
    }

    private void status() {
        if (exitTask.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "autoexit.none"));
            return;
        }
        long left = Math.max(0L, (exitAtMillis - System.currentTimeMillis()) / MILLIS_PER_SECOND);
        chat.showSystemNotice(
                KageI18n.formatOrKey(i18n, "autoexit.left", left / SECONDS_PER_MINUTE, left % SECONDS_PER_MINUTE));
    }

    private void cancel() {
        exitTask.ifPresent(Timer.Task::cancel);
        warnTask.ifPresent(Timer.Task::cancel);
        exitTask = Optional.empty();
        warnTask = Optional.empty();
    }

    private static Timer.Task task(Runnable body) {
        return new Timer.Task() {
            @Override
            public void run() {
                body.run();
            }
        };
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
