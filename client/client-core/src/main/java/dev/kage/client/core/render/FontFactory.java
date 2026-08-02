package dev.kage.client.core.render;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

/**
 * Sinh {@link BitmapFont} runtime từ file {@code .ttf} qua FreeType — KHÔNG dùng bitmap font tĩnh
 * (RESOURCE.md §7, Technical Decision #5): FreeType sinh đúng cỡ chữ cần, hỗ trợ {@code uiScale}
 * (UI.md §6) thay vì phải sinh sẵn 1 bitmap cho mỗi cỡ.
 *
 * <p>Chỉ bọc {@link FreeTypeFontGenerator} ở mức tối thiểu (sinh font + kiểm tra glyph) — CHƯA
 * tích hợp {@code AssetManager}/cache, vì chưa có consumer thật nào cần (chưa có màn hình UI dùng
 * font — xây thêm bây giờ là abstraction thừa trước nhu cầu thật, Điều 30). Nơi nào cần cache/tái
 * dùng theo zone thì bọc thêm ở lớp trên, không sửa lớp này thành "biết" AssetManager.
 */
public final class FontFactory {

    /**
     * Bộ ký tự tiếng Việt đầy đủ: ASCII in được + các nguyên âm/phụ âm tiếng Việt ở Latin-1/Latin
     * Extended-A (àáâã, ăĂ, đĐ, ơƠ, ưƯ...) + toàn bộ khối Latin Extended Additional U+1EA0–U+1EF9
     * (dạng tổ hợp sẵn NFC của mọi nguyên âm mang 1 trong 5 dấu thanh: sắc, huyền, hỏi, ngã, nặng).
     * Dùng làm {@code characters} mặc định khi sinh font cho UI tiếng Việt — FreeType KHÔNG sinh
     * glyph nào ngoài chuỗi này, dù bản thân file {@code .ttf} có chứa glyph đó (RESOURCE.md §7:
     * "nhiều font miễn phí thiếu glyph tiếng Việt có dấu" — nguy cơ tương tự nếu quên khai đủ ở
     * đây dù font có glyph thật).
     */
    public static final String VIETNAMESE_CHARACTERS =
            " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`"
                    + "abcdefghijklmnopqrstuvwxyz{|}~"
                    + "ÀÁÂÃÈÉÊÌÍÒÓÔÕÙÚÝàáâãèéêìíòóôõùúýĂăĐđĨĩŨũƠơƯư"
                    + "ẠạẢảẤấẦầẨẩẪẫẬậẮắẰằẲẳẴẵẶặẸẹẺẻẼẽẾếỀềỂểỄễỆệỈỉỊịỌọỎỏỐốỒồỔổỖỗỘộỚớỜờỞởỠỡỢợỤụỦủỨứỪừỬửỮữỰựỲỳỴỵỶỷỸỹ";

    private FontFactory() {}

    /**
     * Sinh {@link BitmapFont} từ {@code fontFile} ở cỡ {@code sizePx}, chỉ chứa glyph nằm trong
     * {@link #VIETNAMESE_CHARACTERS}.
     *
     * @param fontFile file {@code .ttf}/{@code .otf} nguồn
     * @param sizePx cỡ chữ tính bằng pixel — FreeType sinh trực tiếp ở cỡ này, không scale bitmap
     *     có sẵn (khác bitmap font tĩnh)
     * @return font đã sinh; caller sở hữu, phải gọi {@link BitmapFont#dispose()} khi không dùng nữa
     */
    public static BitmapFont generate(FileHandle fontFile, int sizePx) {
        return generate(fontFile, sizePx, VIETNAMESE_CHARACTERS);
    }

    /**
     * Như {@link #generate(FileHandle, int)} nhưng cho chọn tập ký tự tuỳ ý — VD overlay debug chỉ
     * cần ASCII thì không sinh thừa glyph tiếng Việt không dùng tới.
     *
     * @param fontFile file {@code .ttf}/{@code .otf} nguồn
     * @param sizePx cỡ chữ tính bằng pixel
     * @param characters tập ký tự sẽ được sinh glyph; ký tự nào không nằm trong chuỗi này sẽ KHÔNG
     *     hiển thị được dù font gốc có glyph
     * @return font đã sinh; caller sở hữu, phải gọi {@link BitmapFont#dispose()} khi không dùng nữa
     */
    public static BitmapFont generate(FileHandle fontFile, int sizePx, String characters) {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(fontFile);
        try {
            FreeTypeFontParameter parameter = new FreeTypeFontParameter();
            parameter.size = sizePx;
            parameter.characters = characters;
            return generator.generateFont(parameter);
        } finally {
            generator.dispose();
        }
    }

    /**
     * Kiểm tra {@code fontFile} có glyph thật cho code point {@code codePoint} hay không, tra cmap
     * của FreeType qua {@link FreeTypeFontGenerator#hasGlyph(int)}. KHÔNG cần GL context (không
     * sinh Pixmap/Texture như {@link #generate}) — cho phép chạy trong test/CI headless, dùng để
     * chứng minh coverage glyph tiếng Việt (ROADMAP Phase 1 Exit Criteria "Tiếng Việt có dấu hiển
     * thị đúng").
     *
     * @param fontFile file {@code .ttf}/{@code .otf} nguồn
     * @param codePoint Unicode code point (không phải {@code char} — hỗ trợ ký tự ngoài BMP)
     * @return {@code true} nếu font có glyph thật cho code point này
     */
    public static boolean hasGlyph(FileHandle fontFile, int codePoint) {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(fontFile);
        try {
            return generator.hasGlyph(codePoint);
        } finally {
            generator.dispose();
        }
    }
}
