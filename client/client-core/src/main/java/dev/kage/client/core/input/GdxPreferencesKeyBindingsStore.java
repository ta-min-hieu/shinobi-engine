package dev.kage.client.core.input;

import java.util.OptionalInt;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.client.core.ui.theme.GdxPreferencesLanguageStore;

/** Nơi lưu {@link KeyBindings} (ADR-190) bằng {@code Preferences} của libGDX — chung tệp với cài đặt hiển thị. */
public final class GdxPreferencesKeyBindingsStore implements KeyBindings.Store {

    private static final Logger LOG = LoggerFactory.getLogger(GdxPreferencesKeyBindingsStore.class);

    private final Preferences preferences;

    public GdxPreferencesKeyBindingsStore() {
        this.preferences = Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME);
    }

    @Override
    public OptionalInt read(String key) {
        return preferences.contains(key) ? OptionalInt.of(preferences.getInteger(key)) : OptionalInt.empty();
    }

    @Override
    public void write(String key, int value) {
        preferences.putInteger(key, value);
        flush();
    }

    @Override
    public void remove(String key) {
        preferences.remove(key);
        flush();
    }

    private void flush() {
        try {
            preferences.flush();
        } catch (GdxRuntimeException e) {
            LOG.warn("Không lưu được phím tắt — vẫn có hiệu lực trong phiên này", e);
        }
    }
}
