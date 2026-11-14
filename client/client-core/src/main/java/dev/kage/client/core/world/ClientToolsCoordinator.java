package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.hud.ClockFormat;
import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Ba tiện ích máy khách lô 59, nhớ trên máy: giới hạn FPS (ADR-403, {@code /gioihanfps <mức>} hoặc {@code
 * /fpslimit}), lặp lại lệnh vừa gõ (ADR-404, {@code /.}), giờ 12h/24h (ADR-406, {@code /dinhdanggio 12|24} hoặc
 * {@code /clockformat}).
 */
final class ClientToolsCoordinator implements WindowFlow {

    /** ADR-403 — các mức trần FPS nhận ({@code 0} = không giới hạn). */
    static final Set<Integer> FPS_CAPS = Set.of(0, 30, 60, 90, 120, 144);

    /** ADR-404 — lệnh lặp lại. */
    static final String REPEAT = "/.";

    private static final String FPS_KEY = "display.fps_cap";
    private static final String CLOCK_KEY = "display.clock_12h";
    private static final int NO_SAVED_CAP = -1;

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private IntConsumer fpsSink = fps -> {
        if (Gdx.graphics != null) {
            Gdx.graphics.setForegroundFPS(fps);
        }
    };
    private String lastSlash = "";

    ClientToolsCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        ClockFormat.setTwelveHour(
                preferences.map(prefs -> prefs.getBoolean(CLOCK_KEY, false)).orElse(false));
        int savedCap = preferences
                .map(prefs -> prefs.getInteger(FPS_KEY, NO_SAVED_CAP))
                .orElse(NO_SAVED_CAP);
        if (FPS_CAPS.contains(savedCap)) {
            fpsSink.accept(savedCap);
        }
        chat.useLineRewriter(this::repeat);
        chat.addSlashCommands(this::onSlash);
    }

    /** Thay nơi đặt FPS (test). */
    void useFpsSink(IntConsumer sink) {
        this.fpsSink = sink;
    }

    /** ADR-404 — {@code /.} ⇒ lệnh gạch chéo gần nhất; lệnh gạch chéo khác ⇒ nhớ lại. Không nhớ tin chat thường. */
    String repeat(String typed) {
        String line = typed.strip();
        if (REPEAT.equals(line)) {
            if (lastSlash.isEmpty()) {
                chat.showSystemNotice(KageI18n.textOrKey(i18n, "repeat.none"));
                return "";
            }
            return lastSlash;
        }
        if (line.startsWith("/")) {
            lastSlash = line;
        }
        return typed;
    }

    /** ADR-403 — chuỗi người chơi gõ ⇒ mức FPS hợp lệ. Tĩnh để test. */
    static Optional<Integer> parseCap(String arg) {
        try {
            int cap = Integer.parseInt(arg.strip());
            return FPS_CAPS.contains(cap) ? Optional.of(cap) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty(); // không phải số ⇒ sai cú pháp
        }
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        String arg = words.length > 1 ? words[1].strip() : "";
        switch (command) {
            case "/gioihanfps", "/fpslimit" -> setFps(arg);
            case "/dinhdanggio", "/clockformat" -> setClock(arg);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void setFps(String arg) {
        Optional<Integer> cap = parseCap(arg);
        if (cap.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "fpslimit.usage"));
            return;
        }
        fpsSink.accept(cap.get());
        preferences.ifPresent(prefs -> {
            prefs.putInteger(FPS_KEY, cap.get());
            prefs.flush();
        });
        chat.showSystemNotice(
                cap.get() == 0
                        ? KageI18n.textOrKey(i18n, "fpslimit.off")
                        : KageI18n.formatOrKey(i18n, "fpslimit.set", cap.get()));
    }

    private void setClock(String arg) {
        if (!"12".equals(arg) && !"24".equals(arg)) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "clockformat.usage"));
            return;
        }
        boolean twelve = "12".equals(arg);
        ClockFormat.setTwelveHour(twelve);
        preferences.ifPresent(prefs -> {
            prefs.putBoolean(CLOCK_KEY, twelve);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.textOrKey(i18n, twelve ? "clockformat.12" : "clockformat.24"));
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
