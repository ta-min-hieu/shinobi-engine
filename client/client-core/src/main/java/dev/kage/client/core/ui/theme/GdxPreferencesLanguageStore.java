package dev.kage.client.core.ui.theme;

import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/**
 * {@link LanguageStore} trên libGDX {@link Preferences} — cơ chế lưu cấu hình cục bộ có sẵn trên mọi
 * backend (desktop ghi một file nhỏ dưới thư mục người dùng, Android dùng SharedPreferences), nên
 * {@code client-core} không phải biết đường dẫn file của từng nền tảng (Điều 28).
 *
 * <p>Thư mục trên desktop do launcher đặt ({@code Lwjgl3ApplicationConfiguration#setPreferencesConfig}).
 * Chỉ dùng được sau khi {@code Gdx.app} đã khởi tạo — tức từ bên trong một màn hình, không phải từ
 * {@code main}.
 */
public final class GdxPreferencesLanguageStore implements LanguageStore {

    /** Tên bộ cấu hình của client — các cài đặt người chơi khác sau này dùng chung bộ này. */
    static final String PREFERENCES_NAME = "kage-client";

    static final String LANGUAGE_KEY = "language";

    private final Preferences preferences = Gdx.app.getPreferences(PREFERENCES_NAME);

    @Override
    public Optional<String> readLanguageCode() {
        return preferences.contains(LANGUAGE_KEY) ? Optional.of(preferences.getString(LANGUAGE_KEY)) : Optional.empty();
    }

    @Override
    public void writeLanguageCode(String code) {
        preferences.putString(LANGUAGE_KEY, code);
        preferences.flush();
    }
}
