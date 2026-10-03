package dev.kage.client.core.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Preferences;
import com.badlogic.gdx.files.FileHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * File cấu hình {@code ~/.kage/kage-client} trên máy người chơi — thứ client KHÔNG kiểm soát được:
 * ổ đầy, thư mục chỉ-đọc, phần mềm diệt virus khoá file, người chơi sửa tay hỏng.
 *
 * <p>Dùng {@link Lwjgl3Preferences} THẬT (đúng lớp bản desktop chạy) trỏ vào một đường dẫn không ghi
 * được: thư mục cha của nó là một FILE, nên mọi lần ghi đều hỏng trên mọi hệ điều hành — không phụ
 * thuộc quyền NTFS/POSIX của máy chạy test.
 */
class GdxPreferencesLanguageStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldKeepTheChosenLanguageForThisSession_whenTheSettingsFileCannotBeWritten() throws IOException {
        Path notADirectory = Files.writeString(tempDir.resolve(".kage"), "chiếm chỗ thư mục cấu hình");
        LanguageStore store = new GdxPreferencesLanguageStore(new Lwjgl3Preferences(
                new FileHandle(notADirectory.resolve("kage-client").toFile())));

        // Trước bản vá: GdxRuntimeException bay thẳng ra khỏi listener của nút ngôn ngữ ở màn đăng
        // nhập — tức là bấm "English" làm sập game.
        assertThatCode(() -> LanguageSettings.save(store, ClientLanguage.EN)).doesNotThrowAnyException();
        assertThat(LanguageSettings.load(store)).isEqualTo(ClientLanguage.EN);
    }

    @Test
    void shouldFallBackToDefault_whenTheSettingsFileIsCorrupt() throws IOException {
        Path corrupt = Files.writeString(
                tempDir.resolve("kage-client"),
                "<?xml version=\"1.0\"?><properties><entry key=",
                StandardCharsets.UTF_8);
        LanguageStore store = new GdxPreferencesLanguageStore(new Lwjgl3Preferences(new FileHandle(corrupt.toFile())));

        assertThat(LanguageSettings.load(store)).isEqualTo(ClientLanguage.DEFAULT);
        // Ghi lại được đè lên file hỏng — lần mở game sau đọc đúng.
        LanguageSettings.save(store, ClientLanguage.EN);
        LanguageStore reopened =
                new GdxPreferencesLanguageStore(new Lwjgl3Preferences(new FileHandle(corrupt.toFile())));
        assertThat(LanguageSettings.load(reopened)).isEqualTo(ClientLanguage.EN);
    }
}
