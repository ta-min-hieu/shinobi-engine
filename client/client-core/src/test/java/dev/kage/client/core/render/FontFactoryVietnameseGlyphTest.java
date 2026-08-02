package dev.kage.client.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Chứng minh font tiếng Việt đã chọn (RESOURCE.md §7) có glyph thật cho toàn bộ dấu thanh tiếng
 * Việt — ROADMAP Phase 1 Exit Criteria "Tiếng Việt có dấu hiển thị đúng".
 *
 * <p>Dùng {@link FontFactory#hasGlyph} → {@link FreeTypeFontGenerator#hasGlyph(int)}, tra cmap
 * của FreeType THẬT (native lib {@code gdx-freetype-platform}, không giả lập/không đoán từ tên
 * font) mà KHÔNG cần GL context (khác {@link FontFactory#generate} — không sinh Pixmap/Texture) —
 * nhờ vậy chạy được headless trên CI ({@code ci.yml} chạy {@code ubuntu-latest}, không có
 * Xvfb/display server, một {@code Lwjgl3Application} thật sẽ không khởi động được ở đó).
 */
class FontFactoryVietnameseGlyphTest {

    // Câu mẫu đề bài yêu cầu — phủ đủ 6 thanh điệu (ngang, huyền, sắc, hỏi, ngã, nặng) + toàn bộ
    // nguyên âm/phụ âm riêng tiếng Việt (ăâêôơưđ).
    private static final String SAMPLE_VIETNAMESE_TEXT = "Xin chào, đây là tiếng Việt có dấu: ăâêôơưđ áàảãạ ế ộ ữ";

    private static FileHandle fontFile;

    @BeforeAll
    static void locateBundledFont() {
        Path repoRoot = findRepoRoot();
        File file =
                repoRoot.resolve("assets/raw/fonts/BeVietnamPro-Regular.ttf").toFile();
        assertThat(file)
                .as(
                        "Font tiếng Việt phải nằm ở assets/raw/fonts/ (RESOURCE.md §7) — chưa"
                                + " bundle hoặc đường dẫn sai: %s",
                        file.getAbsolutePath())
                .exists()
                .isFile();
        fontFile = new FileHandle(file);
    }

    @Test
    void hasGlyph_shouldCoverEveryCodePoint_inSampleVietnameseText() {
        assertThat(missingGlyphs(SAMPLE_VIETNAMESE_TEXT))
                .as("Font thiếu glyph cho các ký tự tiếng Việt sau trong câu mẫu — không đủ"
                        + " điều kiện dùng cho UI tiếng Việt (RESOURCE.md §7)")
                .isEmpty();
    }

    @Test
    void hasGlyph_shouldCoverEveryCharacter_inFontFactoryDefaultCharset() {
        assertThat(missingGlyphs(FontFactory.VIETNAMESE_CHARACTERS))
                .as("FontFactory.VIETNAMESE_CHARACTERS liệt kê ký tự mà font KHÔNG có glyph —"
                        + " FontFactory#generate() sẽ âm thầm bỏ qua ký tự này lúc runtime")
                .isEmpty();
    }

    private static List<String> missingGlyphs(String text) {
        List<String> missing = new ArrayList<>();
        text.codePoints().distinct().forEach(codePoint -> {
            if (!FontFactory.hasGlyph(fontFile, codePoint)) {
                missing.add(describe(codePoint));
            }
        });
        return missing;
    }

    private static String describe(int codePoint) {
        return new String(Character.toChars(codePoint))
                + " (U+"
                + Integer.toHexString(codePoint).toUpperCase(Locale.ROOT)
                + ")";
    }

    private static Path findRepoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (dir.resolve("settings.gradle.kts").toFile().exists()) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Không tìm thấy settings.gradle.kts từ thư mục làm việc "
                + Path.of("").toAbsolutePath());
    }
}
