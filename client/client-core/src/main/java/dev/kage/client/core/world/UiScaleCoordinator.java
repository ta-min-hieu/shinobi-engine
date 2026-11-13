package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Thu phóng giao diện (ADR-308) — {@code /giaodien <80-150>} (hoặc {@code /uiscale}): phóng to/thu nhỏ MỌI cửa sổ HUD
 * đăng ký trong {@link PanelHub}; nhớ trong Preferences {@code ui.scale}. Không đụng thế giới 3D.
 */
final class UiScaleCoordinator implements WindowFlow {

    /** Phần trăm nhỏ / lớn nhất. */
    static final int MIN_PERCENT = 80;

    static final int MAX_PERCENT = 150;

    private static final int DEFAULT_PERCENT = 100;
    private static final float PERCENT = 100f;
    private static final String PREF_KEY = "ui.scale";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Optional<Preferences> preferences;
    private Optional<PanelHub> hub = Optional.empty();
    private int percent;

    UiScaleCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        this.preferences = Gdx.app == null
                ? Optional.empty()
                : Optional.of(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
        this.percent = preferences
                .map(prefs -> Math.clamp(prefs.getInteger(PREF_KEY, DEFAULT_PERCENT), MIN_PERCENT, MAX_PERCENT))
                .orElse(DEFAULT_PERCENT);
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối hub cửa sổ và áp ngay cỡ đã nhớ. */
    void useHub(PanelHub panels) {
        this.hub = Optional.of(panels);
        apply();
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/giaodien".equals(command) && !"/uiscale".equals(command)) {
            return false;
        }
        OptionalInt parsed = parse(words.length > 1 ? words[1].strip() : "");
        if (parsed.isEmpty()) {
            chat.showSystemNotice(KageI18n.formatOrKey(i18n, "uiscale.usage", MIN_PERCENT, MAX_PERCENT, percent));
            return true;
        }
        percent = parsed.getAsInt();
        apply();
        preferences.ifPresent(prefs -> {
            prefs.putInteger(PREF_KEY, percent);
            prefs.flush();
        });
        chat.showSystemNotice(KageI18n.formatOrKey(i18n, "uiscale.set", percent));
        return true;
    }

    private void apply() {
        if (Gdx.graphics != null) {
            hub.ifPresent(
                    panels -> panels.setUiScale(percent / PERCENT, Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
        }
    }

    /** "80".."150" (bỏ dấu % nếu có). Tĩnh để test. */
    static OptionalInt parse(String text) {
        String digits = text.endsWith("%") ? text.substring(0, text.length() - 1) : text;
        if (digits.isEmpty() || digits.length() > 3 || !digits.chars().allMatch(Character::isDigit)) {
            return OptionalInt.empty();
        }
        int value = Integer.parseInt(digits);
        return value < MIN_PERCENT || value > MAX_PERCENT ? OptionalInt.empty() : OptionalInt.of(value);
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
