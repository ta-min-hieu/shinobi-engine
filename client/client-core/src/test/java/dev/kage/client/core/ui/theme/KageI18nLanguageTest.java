package dev.kage.client.core.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.I18NBundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link KageI18n} nạp ĐÚNG file bundle theo ngôn ngữ — dựng bundle THẬT từ resource của module (cùng
 * cách {@code KageI18nMissingKeyTest}: {@code new FileHandle(File)}, không cần libGDX khởi tạo).
 *
 * <p>Đáng đo vì {@code I18NBundle} có đường lùi ngầm: không thấy file của locale được hỏi thì nó thử
 * locale MẶC ĐỊNH của JVM rồi file gốc. Một lỗi tên file/mã ngôn ngữ sẽ không làm sập gì cả — chỉ lặng
 * lẽ hiện tiếng Việt cho người đã chọn tiếng Anh. Chỉ so CHỮ thật mới bắt được.
 */
class KageI18nLanguageTest {

    private static FileHandle bundleBase;

    @BeforeAll
    static void locateBundle() throws URISyntaxException {
        URL vietnamese = KageI18nLanguageTest.class.getResource("/i18n/strings_vi.properties");
        assertThat(vietnamese)
                .as("bundle tiếng Việt phải có trên classpath test")
                .isNotNull();
        File folder = new File(vietnamese.toURI()).getParentFile();
        bundleBase = new FileHandle(new File(folder, "strings"));
    }

    @AfterEach
    void restoreDefaultLanguage() {
        // Trạng thái của TIẾN TRÌNH — không để rò sang test khác chạy cùng JVM.
        KageI18n.useLanguage(ClientLanguage.DEFAULT);
    }

    @Test
    void loadsTheVietnameseBundleForVietnamese() {
        I18NBundle bundle = KageI18n.load(bundleBase, ClientLanguage.VI);

        assertThat(bundle.get("login.button.connect")).isEqualTo("Kết nối");
        assertThat(bundle.format("login.language.current", bundle.get("language.name.vi")))
                .isEqualTo("Ngôn ngữ: Tiếng Việt");
    }

    @Test
    void loadsTheEnglishBundleForEnglish() {
        I18NBundle bundle = KageI18n.load(bundleBase, ClientLanguage.EN);

        assertThat(bundle.get("login.button.connect")).isEqualTo("Connect");
        assertThat(bundle.format("login.language.current", bundle.get("language.name.en")))
                .isEqualTo("Language: English");
    }

    @Test
    void serverErrorKeysFollowTheChosenLanguage() {
        // error_key từ server được dịch HOÀN TOÀN phía client — đổi ngôn ngữ là câu lỗi đổi theo,
        // không cần server biết gì.
        I18NBundle english = KageI18n.load(bundleBase, ClientLanguage.EN);
        I18NBundle vietnamese = KageI18n.load(bundleBase, ClientLanguage.VI);

        assertThat(KageI18n.errorText(english, "error.logged_in_elsewhere", "login.error.connect_failed"))
                .startsWith("This character just entered the world");
        assertThat(KageI18n.errorText(vietnamese, "error.logged_in_elsewhere", "login.error.connect_failed"))
                .startsWith("Nhân vật này vừa vào thế giới");
        // Khoá lạ rơi về câu chung — của ĐÚNG ngôn ngữ đã chọn.
        assertThat(KageI18n.errorText(english, "error.not_a_real_key", "login.error.connect_failed"))
                .isEqualTo("Connection failed. Please try again.");
    }

    @Test
    void languageNamesAreWrittenInTheirOwnLanguageInEveryBundle() {
        for (ClientLanguage bundleLanguage : ClientLanguage.values()) {
            I18NBundle bundle = KageI18n.load(bundleBase, bundleLanguage);
            assertThat(bundle.get(ClientLanguage.VI.nameKey())).isEqualTo("Tiếng Việt");
            assertThat(bundle.get(ClientLanguage.EN.nameKey())).isEqualTo("English");
        }
    }

    @Test
    void useLanguageChangesWhatTheNextLoadUses() {
        assertThat(KageI18n.currentLanguage()).isEqualTo(ClientLanguage.DEFAULT);

        KageI18n.useLanguage(ClientLanguage.EN);

        assertThat(KageI18n.currentLanguage()).isEqualTo(ClientLanguage.EN);
    }
}
