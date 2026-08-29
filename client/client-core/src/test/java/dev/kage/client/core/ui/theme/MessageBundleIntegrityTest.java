package dev.kage.client.core.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.render.FontFactory;

/**
 * Gác bốn thứ mà không có gì khác trong build bắt được — bundle i18n không được biên dịch, nên mọi
 * hư hỏng trong đó chỉ lộ ra khi người chơi nhìn thấy chữ sai trên màn hình.
 *
 * <p><b>Bài học có thật, không phải phòng xa.</b> Vòng gộp song song 2026-08-28 để lọt nguyên ba
 * dòng dấu xung đột git ({@code <<<<<<< HEAD}, {@code =======}, {@code >>>>>>> worktree-agent-...})
 * vào CẢ HAI bundle và commit thẳng lên master. Build vẫn xanh, 948 test vẫn xanh: file
 * {@code .properties} không được biên dịch, {@code Properties.load} nuốt gọn dòng rác thành một khoá
 * vô nghĩa thay vì báo lỗi, và không test nào đọc toàn bộ bundle. Test này đọc.
 *
 * <p>Kiểm tra thứ hai — hai bundle phải có ĐÚNG cùng tập khoá — bắt lỗi phổ biến hơn nhiều: thêm
 * chữ tiếng Việt mà quên bản tiếng Anh thì người chơi đổi ngôn ngữ sẽ thấy đúng cái khoá trần
 * ({@code quest.log.title}) hiện lên giữa giao diện.
 */
class MessageBundleIntegrityTest {

    /** Mọi bundle client phải có; thêm ngôn ngữ mới thì thêm vào đây. */
    private static final List<String> BUNDLES = List.of("strings_vi.properties", "strings_en.properties");

    /** Dấu xung đột git, đúng như git ghi ra: ba ký tự lặp bảy lần ở đầu dòng. */
    private static final List<String> CONFLICT_MARKERS = List.of("<<<<<<<", "=======", ">>>>>>>");

    @Test
    @DisplayName("Không bundle nào chứa dấu xung đột git còn sót lại")
    void hasNoLeftoverConflictMarker() {
        for (String bundle : BUNDLES) {
            List<String> lines = lines(bundle);
            for (String marker : CONFLICT_MARKERS) {
                assertThat(lines)
                        .as("%s còn dấu gộp \"%s\" — một lần gộp song song đã lọt lên master như thế", bundle, marker)
                        .noneMatch(line -> line.startsWith(marker));
            }
        }
    }

    @Test
    @DisplayName("Bundle tiếng Việt và tiếng Anh có ĐÚNG cùng tập khoá")
    void bundlesHaveIdenticalKeys() {
        assertThat(keys("strings_vi.properties")).isEqualTo(keys("strings_en.properties"));
    }

    @Test
    @DisplayName("Không khoá nào có giá trị rỗng — khoá rỗng nhìn giống hệt lỗi thiếu chữ")
    void hasNoEmptyValue() {
        for (String bundle : BUNDLES) {
            Properties properties = load(bundle);
            for (String key : properties.stringPropertyNames()) {
                assertThat(properties.getProperty(key).trim())
                        .as("%s: khoá %s có giá trị rỗng", bundle, key)
                        .isNotEmpty();
            }
        }
    }

    @Test
    @DisplayName("Mọi ký tự trong bundle đều có glyph — font UI là tập ĐÓNG, thiếu là mất chữ trong im lặng")
    void everyCharacterHasAGlyph() {
        for (String bundle : BUNDLES) {
            Properties properties = load(bundle);
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                for (int index = 0; index < value.length(); index++) {
                    char character = value.charAt(index);
                    if (Character.isISOControl(character)) {
                        // Xuống dòng/tab là ký tự BỐ CỤC do Label tự xử lý, không phải chữ cần vẽ —
                        // hud.hint.jump có một dòng xuống thật. Đòi glyph cho chúng là dương tính giả.
                        continue;
                    }
                    assertThat(FontFactory.canRender(character))
                            .as(
                                    "%s: khoá %s chứa ký tự U+%04X ('%c') không có trong"
                                            + " FontFactory.VIETNAMESE_CHARACTERS — BitmapFont sẽ BỎ QUA nó"
                                            + " và người chơi đọc một câu thiếu chữ, không có lỗi nào được báo",
                                    bundle, key, (int) character, character)
                            .isTrue();
                }
            }
        }
    }

    private static SortedSet<String> keys(String bundle) {
        SortedSet<String> keys = new TreeSet<>(load(bundle).stringPropertyNames());
        assertThat(keys).as("%s rỗng — đường dẫn resource sai?", bundle).isNotEmpty();
        return keys;
    }

    private static List<String> lines(String bundle) {
        return read(bundle).lines().toList();
    }

    private static Properties load(String bundle) {
        Properties properties = new Properties();
        try (Reader reader = new java.io.StringReader(read(bundle))) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }

    /** Đọc bằng UTF-8 tường minh: {@code Properties.load(InputStream)} mặc định ISO-8859-1. */
    private static String read(String bundle) {
        InputStream stream = MessageBundleIntegrityTest.class.getResourceAsStream("/i18n/" + bundle);
        assertThat(stream).as("không tìm thấy /i18n/%s trên classpath", bundle).isNotNull();
        // Reader trong try-with-resources chứ không phải InputStream: đóng Reader đóng luôn stream
        // bên dưới, còn đóng mỗi stream thì Reader bọc ngoài không bao giờ được đóng (OS_OPEN_STREAM).
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            StringBuilder text = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                text.append(buffer, 0, read);
            }
            return text.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
