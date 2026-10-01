package dev.kage.client.core.ui.theme;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeBitmapFontData;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.utils.GdxNativesLoader;

import dev.kage.client.core.render.FontFactory;

/**
 * Đo bề rộng chữ (px) đúng như font UI vẽ nó — <b>không cần GL context</b>, nên chạy được trong
 * JUnit và trên CI không có màn hình.
 *
 * <p>Cùng font, cùng cỡ ({@link KageSkin#FONT_SIZE_PX}), cùng tập ký tự ({@link FontFactory#VIETNAMESE_CHARACTERS})
 * mà {@link KageSkin} sinh lúc chạy. Mẹo để khỏi cần GL: tự đưa một {@link PixmapPacker} cho
 * FreeType — khi đó {@code generateData} KHÔNG tự dựng {@code Texture} (thứ duy nhất đòi GL), còn
 * số đo glyph ({@code xadvance}, kerning) thì giống hệt.
 *
 * <p>Bề rộng một dòng = tổng {@code xadvance} + kerning từng cặp — hơi DƯ so với {@code GlyphLayout}
 * (glyph cuối dùng {@code xadvance} thay vì bề rộng thật), tức là đo nghiêng về phía báo tràn. Đúng
 * chiều cần cho một cổng chặn tràn chữ.
 */
public final class TextWidthMeter implements AutoCloseable {

    /**
     * Khoảng trống tối thiểu giữa chữ và viền nút, mỗi bên — cỡ bằng viền khung của
     * {@code KageSkin.framed}, để chữ không đè lên viền. Không đòi hơn: lần rà bằng ảnh chụp
     * 2026-09-05 xác nhận các nút đang ship (chữ sát viền) trông vẫn đúng.
     */
    public static final float MIN_SIDE_MARGIN_PX = 4f;

    private static final int PACKER_PAGE_PX = 1024;
    private static final int PACKER_PADDING_PX = 2;

    private final FreeTypeFontGenerator generator;
    private final PixmapPacker packer;
    private final FreeTypeBitmapFontData data;

    public TextWidthMeter() {
        GdxNativesLoader.load(); // Pixmap của PixmapPacker cần native gdx; FreeType tự nạp phần của nó.
        this.generator = new FreeTypeFontGenerator(new FileHandle(findRepoRoot()
                .resolve("assets/raw/fonts/BeVietnamPro-Regular.ttf")
                .toFile()));
        this.packer =
                new PixmapPacker(PACKER_PAGE_PX, PACKER_PAGE_PX, Pixmap.Format.RGBA8888, PACKER_PADDING_PX, false);
        FreeTypeFontParameter parameter = new FreeTypeFontParameter();
        parameter.size = KageSkin.FONT_SIZE_PX;
        parameter.characters = FontFactory.VIETNAMESE_CHARACTERS;
        parameter.packer = packer;
        this.data = generator.generateData(parameter);
    }

    /**
     * Bề rộng dòng DÀI NHẤT của {@code text} (nhãn có thể có {@code \n}), nhân {@code fontScale}
     * (VD ô gợi ý phím của CombatHud vẽ chữ ở 0,65).
     */
    public float widestLine(String text, float fontScale) {
        float widest = 0f;
        for (String line : text.split("\n", -1)) {
            widest = Math.max(widest, lineWidth(line));
        }
        return widest * fontScale;
    }

    private float lineWidth(String line) {
        float width = 0f;
        Glyph previous = null;
        for (int index = 0; index < line.length(); index++) {
            Glyph glyph = data.getGlyph(line.charAt(index));
            if (glyph == null) {
                // Ký tự không có glyph bị BỎ QUA khi vẽ — MessageBundleIntegrityTest canh chuyện đó.
                continue;
            }
            if (previous != null) {
                width += previous.getKerning(line.charAt(index));
            }
            width += glyph.xadvance;
            previous = glyph;
        }
        return width;
    }

    /**
     * Mọi nhãn (theo khoá) KHÔNG vừa một nút rộng cố định {@code buttonWidthPx}, xét ở MỌI ngôn ngữ.
     *
     * <p>Nền nút của {@link KageSkin#buttonStyle} không có padding, nên chữ được phép chiếm cả bề rộng
     * — trừ {@link #MIN_SIDE_MARGIN_PX} mỗi bên để chữ không dính vào viền.
     *
     * @return mỗi phần tử một dòng mô tả chỗ tràn; rỗng = vừa hết
     */
    public List<String> overflows(String where, float buttonWidthPx, float fontScale, List<String> keys) {
        List<String> problems = new ArrayList<>();
        float available = buttonWidthPx - (2f * MIN_SIDE_MARGIN_PX);
        for (ClientLanguage language : ClientLanguage.values()) {
            Properties bundle = bundle(language);
            for (String key : keys) {
                String text = bundle.getProperty(key);
                if (text == null) {
                    problems.add(where + " [" + language.code() + "] thiếu khoá " + key);
                    continue;
                }
                float needed = widestLine(text, fontScale);
                if (needed > available) {
                    problems.add(String.format(
                            Locale.ROOT,
                            "%s [%s] %s = \"%s\" cần %.0fpx, nút chỉ có %.0fpx",
                            where,
                            language.code(),
                            key,
                            text,
                            needed + (2f * MIN_SIDE_MARGIN_PX),
                            buttonWidthPx));
                }
            }
        }
        return problems;
    }

    @Override
    public void close() {
        packer.dispose();
        generator.dispose();
    }

    /** Bundle thật trên classpath, đọc UTF-8 tường minh ({@code Properties.load(InputStream)} mặc định ISO-8859-1). */
    public static Properties bundle(ClientLanguage language) {
        String resource = "/i18n/strings_" + language.code() + ".properties";
        InputStream stream = TextWidthMeter.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("không tìm thấy " + resource + " trên classpath");
        }
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
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
