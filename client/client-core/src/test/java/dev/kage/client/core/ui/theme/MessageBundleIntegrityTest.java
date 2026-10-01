package dev.kage.client.core.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.render.FontFactory;

/**
 * Gác năm thứ mà không có gì khác trong build bắt được — bundle i18n không được biên dịch, nên mọi
 * hư hỏng trong đó chỉ lộ ra khi người chơi nhìn thấy chữ sai trên màn hình.
 *
 * <p><b>Bài học có thật, không phải phòng xa.</b> Vòng gộp song song 2026-08-28 để lọt nguyên ba
 * dòng dấu xung đột git ({@code <<<<<<< HEAD}, {@code =======}, {@code >>>>>>> worktree-agent-...})
 * vào CẢ HAI bundle và commit thẳng lên master. Build vẫn xanh, 948 test vẫn xanh: file
 * {@code .properties} không được biên dịch, {@code Properties.load} nuốt gọn dòng rác thành một khoá
 * vô nghĩa thay vì báo lỗi, và không test nào đọc toàn bộ bundle. Test này đọc.
 *
 * <p><b>Bài học có thật thứ hai (2026-08-30).</b> Thi hành ADR-036 suýt thêm một khoá
 * {@code error.level_too_low} thứ hai vào cả hai bundle — khoá đó ĐÃ thuộc về luồng chọn hệ phái
 * ("Chưa đủ cấp 10 để chọn hệ phái"). {@link Properties#load} lấy dòng SAU CÙNG và vứt dòng trước
 * mà không kêu một tiếng, nên hậu quả sẽ là luồng chọn hệ phái im lặng hiện câu của luồng mặc đồ.
 * Ba kiểm tra cũ đều xanh với lỗi đó: tập khoá vẫn khớp nhau, không khoá nào rỗng, chữ vẫn có
 * glyph. {@link #hasNoDuplicateKey()} là chỗ bắt.
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

    /** Từ ba từ trở lên mới tính là một câu (tên riêng như "Tiếng Việt" chỉ có hai). */
    private static final int MIN_WORDS_OF_A_SENTENCE = 3;

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

    /**
     * Rà 2026-10-01 (lúc mở song ngữ): TÁM khoá {@code error.*} của server mang nguyên câu tiếng Anh
     * trong bundle tiếng Việt — bản sao chép để qua cổng {@code verifyServerErrorKeys} rồi không ai dịch.
     * Cả năm kiểm tra khác đều xanh với lỗi đó (khoá có, giá trị không rỗng, ký tự có glyph). Một
     * CÂU (từ ba từ trở lên) giống hệt nhau ở hai bundle gần như chắc chắn là chưa dịch; tên riêng,
     * số, ký hiệu ngắn ("{0} ms", "x{0}", "English") thì được phép trùng.
     */
    @Test
    @DisplayName("Không câu nào ở bundle tiếng Việt là bản sao nguyên văn của câu tiếng Anh")
    void vietnameseBundleHasNoUntranslatedEnglishSentence() {
        Properties vietnamese = load("strings_vi.properties");
        Properties english = load("strings_en.properties");
        List<String> untranslated = vietnamese.stringPropertyNames().stream()
                .filter(key -> vietnamese.getProperty(key).equals(english.getProperty(key)))
                .filter(key -> wordCount(vietnamese.getProperty(key)) >= MIN_WORDS_OF_A_SENTENCE)
                .sorted()
                .toList();
        assertThat(untranslated)
                .as("Các khoá này hiện câu TIẾNG ANH cho người chơi đã chọn tiếng Việt")
                .isEmpty();
    }

    @Test
    @DisplayName("Không khoá nào bị khai báo hai lần — Properties.load lấy dòng sau và nuốt dòng trước")
    void hasNoDuplicateKey() {
        for (String bundle : BUNDLES) {
            List<String> declared = declaredKeysInFileOrder(bundle);
            SortedSet<String> unique = new TreeSet<>(declared);
            assertThat(declared)
                    .as(
                            "%s khai báo %d dòng khoá nhưng chỉ có %d khoá khác nhau — khoá trùng bị"
                                    + " ghi đè trong im lặng, câu của chỗ khai báo TRƯỚC biến mất khỏi giao diện"
                                    + " mà không có lỗi nào được báo",
                            bundle, declared.size(), unique.size())
                    .hasSize(unique.size());
        }
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

    /**
     * Mọi khoá ĐƯỢC KHAI BÁO trong file, theo thứ tự xuất hiện và GIỮ NGUYÊN bản trùng — khác
     * {@link #keys} vốn đi qua {@link Properties} nên bản trùng đã bị gộp mất từ trước.
     *
     * <p>Đọc thô bằng tay là cố ý: chính việc {@code Properties} âm thầm gộp trùng là thứ cần bắt.
     * Bundle của dự án chỉ dùng khuôn {@code khoá=giá trị} một dòng, không có dòng nối bằng dấu
     * chéo ngược, không có {@code khoá:giá trị} — nếu khuôn đó đổi thì hàm này phải đổi theo.
     */
    private static List<String> declaredKeysInFileOrder(String bundle) {
        return lines(bundle).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#") && !line.startsWith("!"))
                .filter(line -> line.indexOf('=') > 0)
                .map(line -> line.substring(0, line.indexOf('=')).strip())
                .toList();
    }

    /** Số từ chữ cái (bỏ qua ô {@code {0}}, số, ký hiệu). */
    private static long wordCount(String value) {
        return Arrays.stream(value.split("[^\\p{L}]+"))
                .filter(word -> word.length() > 1)
                .count();
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
