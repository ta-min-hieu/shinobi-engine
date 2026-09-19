package dev.kage.client.core.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.I18NBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Đo trên {@code I18NBundle} THẬT, không phải hàm thuần — vì thứ cần chứng minh nằm đúng ở chỗ
 * {@link KageI18nTest} cố tình không chạm tới: hành vi của bundle khi khoá thiếu.
 *
 * <p>Suốt một thời gian dài, 24 lớp HUD/coordinator mỗi lớp tự viết
 * {@code try (bundle.get) catch (MissingResourceException) return key} — 35 chỗ, và
 * <b>không chỗ nào từng chạy</b>: {@code setExceptionOnMissingKey} là cờ <b>static trên cả lớp</b>
 * {@code I18NBundle}, mà {@link KageI18n#load()} tắt nó. Ca {@code bundleReturnsTheMarkerAndNeverThrows}
 * là phép đo khoá lại đúng sự thật ấy: gỡ {@link KageI18n#textOrKey} ra và trả
 * {@code bundle.get} thẳng thì ca dưới nó đỏ ngay.
 *
 * <p>Dựng bundle bằng {@link FileHandle} bọc {@code java.io.File} (type {@code Absolute}) nên
 * KHÔNG cần {@code Gdx.files} — đây là lý do {@code KageI18nTest} tưởng phải tránh bundle thật.
 */
class KageI18nMissingKeyTest {

    private static final String PRESENT_KEY = "quest.name.kill_training_dummies";
    private static final String MISSING_KEY = "quest.name.foundation_breakthrough";
    private static final String COUNTED_KEY = "guild.list.truncated";

    private static I18NBundle bundleIn(Path folder) throws IOException {
        Path properties = folder.resolve("strings.properties");
        Files.writeString(
                properties,
                PRESENT_KEY + "=Đập Mộc Nhân\n" + COUNTED_KEY + "=còn {0} dòng nữa\n",
                StandardCharsets.UTF_8);
        // Đúng dòng mà KageI18n.load() chạy — ca đo này vô nghĩa nếu thiếu nó.
        I18NBundle.setExceptionOnMissingKey(false);
        return I18NBundle.createBundle(new FileHandle(folder.resolve("strings").toFile()), Locale.of("vi"), "UTF-8");
    }

    @Test
    void bundleReturnsTheMarkerAndNeverThrows(@TempDir Path folder) throws IOException {
        I18NBundle bundle = bundleIn(folder);

        // KHÔNG ném: mọi catch (MissingResourceException) quanh lời gọi này là code chết.
        assertThat(bundle.get(MISSING_KEY)).isEqualTo("???" + MISSING_KEY + "???");
        // Và format cũng vậy — tham số bị MessageFormat nuốt vì chuỗi mốc không có ô {0} nào.
        assertThat(bundle.format(MISSING_KEY, 7)).isEqualTo("???" + MISSING_KEY + "???");
    }

    @Test
    void textOrKeyFallsBackToTheBareKey(@TempDir Path folder) throws IOException {
        I18NBundle bundle = bundleIn(folder);

        assertThat(KageI18n.textOrKey(bundle, MISSING_KEY)).isEqualTo(MISSING_KEY);
        assertThat(KageI18n.textOrKey(bundle, PRESENT_KEY)).isEqualTo("Đập Mộc Nhân");
    }

    @Test
    void formatOrKeyFallsBackToTheBareKeyAndStillFormatsWhenPresent(@TempDir Path folder) throws IOException {
        I18NBundle bundle = bundleIn(folder);

        assertThat(KageI18n.formatOrKey(bundle, MISSING_KEY, 7)).isEqualTo(MISSING_KEY);
        assertThat(KageI18n.formatOrKey(bundle, COUNTED_KEY, 7)).isEqualTo("còn 7 dòng nữa");
    }

    @Test
    void errorTextStillPrefersTheFallbackKeyOverTheMarker(@TempDir Path folder) throws IOException {
        I18NBundle bundle = bundleIn(folder);

        assertThat(KageI18n.errorText(bundle, MISSING_KEY, PRESENT_KEY)).isEqualTo("Đập Mộc Nhân");
        assertThat(KageI18n.errorText(bundle, PRESENT_KEY, COUNTED_KEY)).isEqualTo("Đập Mộc Nhân");
    }
}
