package dev.kage.client.core.ui.theme;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Cài đặt hiển thị (ADR-143) — công tắc thuần HÌNH, lưu trên MÁY người chơi (không phải server: không phải dữ liệu
 * gameplay). Mỗi lớp vẽ cầm một {@link BooleanSupplier} của công tắc mình qua {@link #switchFor} và tự hỏi mỗi khung —
 * đọc một ô cờ, không cấp phát.
 */
public final class DisplaySettings {

    /** Các công tắc — mặc định đều BẬT (hình như trước khi có cài đặt). */
    public enum Option {
        /** Danh hiệu cảnh giới + nhãn Ma Đầu trên đầu (ADR-140/142). */
        REALM_TITLES("display.realm_titles"),
        /** Số damage / né / chí mạng bay lên. */
        DAMAGE_NUMBERS("display.damage_numbers"),
        /** Núi xa/gần và mây trôi của hậu cảnh (ART-PIPELINE §2.8) — tắt còn trời và biển mây phẳng. */
        PARALLAX("display.parallax"),
        /** Mưa linh khí / bão cát theo vùng (ADR-144). */
        WEATHER("display.weather"),
        /** ADR-156 — tự uống đan hồi máu khi máu thấp; mặc định TẮT (đổi hành vi chơi, không chỉ hiển thị). */
        AUTO_POTION("display.auto_potion", false);

        private final String key;
        private final boolean defaultOn;

        Option(String key) {
            this(key, true);
        }

        Option(String key, boolean defaultOn) {
            this.key = key;
            this.defaultOn = defaultOn;
        }

        /** Giá trị khi người chơi chưa từng bật/tắt. */
        public boolean defaultOn() {
            return defaultOn;
        }

        /** Khoá lưu và cũng là gốc khoá i18n ({@code settings.<key>}). */
        public String key() {
            return key;
        }
    }

    /** Nơi lưu — tách port để test không cần libGDX; bản thật là {@link GdxPreferencesDisplaySettingsStore}. */
    public interface Store {
        /** Giá trị đã lưu, rỗng nếu chưa lưu bao giờ. */
        Optional<Boolean> read(String key);

        /** Ghi và đẩy xuống nơi lưu ngay; KHÔNG ném khi nơi lưu hỏng (giá trị vẫn có hiệu lực trong phiên). */
        void write(String key, boolean value);
    }

    /** Một công tắc — ô cờ đổi được; {@link #switchFor} trả chính {@code isOn} của nó. */
    private static final class Flag implements BooleanSupplier {
        private boolean on;

        Flag(boolean on) {
            this.on = on;
        }

        @Override
        public boolean getAsBoolean() {
            return on;
        }
    }

    private final Store store;
    private final Map<Option, Flag> flags = new EnumMap<>(Option.class);

    private DisplaySettings(Store store) {
        this.store = store;
        for (Option option : Option.values()) {
            flags.put(option, new Flag(store.read(option.key()).orElse(option.defaultOn())));
        }
    }

    /** Đọc mọi công tắc từ {@code store}. */
    public static DisplaySettings load(Store store) {
        return new DisplaySettings(store);
    }

    public boolean isOn(Option option) {
        return flags.get(option).on;
    }

    /** Đảo một công tắc và lưu ngay. */
    public void toggle(Option option) {
        Flag flag = flags.get(option);
        flag.on = !flag.on;
        store.write(option.key(), flag.on);
    }

    /** Công tắc dạng hỏi-mỗi-khung cho một lớp vẽ — cùng một đối tượng mỗi lần gọi. */
    public BooleanSupplier switchFor(Option option) {
        return flags.get(option);
    }
}
