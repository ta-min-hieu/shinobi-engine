package dev.kage.client.core.ui.theme;

import java.util.Optional;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.GdxRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOG = LoggerFactory.getLogger(GdxPreferencesLanguageStore.class);

    /** Tên bộ cấu hình của client — các cài đặt người chơi khác sau này dùng chung bộ này. */
    public static final String PREFERENCES_NAME = "kage-client";

    static final String LANGUAGE_KEY = "language";

    private final Preferences preferences;

    public GdxPreferencesLanguageStore() {
        this(Gdx.app.getPreferences(PREFERENCES_NAME));
    }

    /** Cho test — nơi lưu dựng thẳng trên một file, không cần {@code Gdx.app}. */
    GdxPreferencesLanguageStore(Preferences preferences) {
        this.preferences = preferences;
    }

    @Override
    public Optional<String> readLanguageCode() {
        return preferences.contains(LANGUAGE_KEY) ? Optional.of(preferences.getString(LANGUAGE_KEY)) : Optional.empty();
    }

    @Override
    public void writeLanguageCode(String code) {
        preferences.putString(LANGUAGE_KEY, code);
        try {
            preferences.flush();
        } catch (GdxRuntimeException e) {
            // Ổ đầy, thư mục chỉ-đọc, file bị khoá... là chuyện của máy người chơi, không phải lý do
            // làm sập màn đăng nhập (exception bay ra khỏi listener của nút ngôn ngữ là sập cả game).
            // Giá trị vẫn nằm trong bộ nhớ nên lựa chọn có hiệu lực cho tới khi tắt game; chỉ lần mở
            // sau mới quên (GdxPreferencesLanguageStoreTest).
            LOG.warn("Không ghi được cài đặt ngôn ngữ xuống máy — chỉ giữ cho phiên này", e);
        }
    }
}
