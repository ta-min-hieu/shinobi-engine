package dev.kage.client.core.ui.theme;

import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link DisplaySettings.Store} trên libGDX {@link Preferences} — dùng CHUNG bộ cấu hình {@code kage-client} với cài
 * đặt ngôn ngữ ({@link GdxPreferencesLanguageStore}). Chỉ dùng được sau khi {@code Gdx.app} đã khởi tạo.
 */
public final class GdxPreferencesDisplaySettingsStore implements DisplaySettings.Store {

    private static final Logger LOG = LoggerFactory.getLogger(GdxPreferencesDisplaySettingsStore.class);

    private final Preferences preferences;

    public GdxPreferencesDisplaySettingsStore() {
        this(Gdx.app.getPreferences(GdxPreferencesLanguageStore.PREFERENCES_NAME));
    }

    /** Cho test — nơi lưu dựng thẳng, không cần {@code Gdx.app}. */
    GdxPreferencesDisplaySettingsStore(Preferences preferences) {
        this.preferences = preferences;
    }

    @Override
    public Optional<Boolean> read(String key) {
        return preferences.contains(key) ? Optional.of(preferences.getBoolean(key)) : Optional.empty();
    }

    @Override
    public void write(String key, boolean value) {
        preferences.putBoolean(key, value);
        try {
            preferences.flush();
        } catch (GdxRuntimeException e) {
            // Cùng lý do với GdxPreferencesLanguageStore: máy người chơi hỏng chỗ ghi không được làm sập game.
            LOG.warn("Không ghi được cài đặt hiển thị xuống máy — chỉ giữ cho phiên này", e);
        }
    }
}
