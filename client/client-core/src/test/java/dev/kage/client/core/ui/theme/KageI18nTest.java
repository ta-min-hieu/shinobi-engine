package dev.kage.client.core.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@code I18NBundle} báo khoá thiếu bằng GIÁ TRỊ TRẢ VỀ, không bằng exception.
 *
 * <p>{@link KageI18n#load()} gọi {@code setExceptionOnMissingKey(false)} để một khoá thiếu không
 * làm sập UI. Hệ quả dễ viết sai: một {@code try/catch (MissingResourceException)} quanh
 * {@code bundle.get} trông như đang phòng thủ mà **không bao giờ chạy**, và
 * {@code ???error.abc???} hiện thẳng lên màn hình người chơi. Bản đầu của chính lần sửa 2026-09-19
 * viết đúng kiểu đó, nên chỗ nhận biết được tách ra và đo riêng.
 *
 * <p>Ở đây chỉ đo hàm thuần. ⚠️ Lý do cũ ghi ở chỗ này — *"dựng bundle thật thì cần
 * {@code Gdx.files}"* — là SAI, và nó đắt: chính vì không ai dựng bundle thật mà cả hàm lẫn test
 * cùng đếm HAI dấu hỏi trong khi {@code I18NBundle} trả về BA. {@code KageI18nMissingKeyTest}
 * dựng bundle thật bằng {@code new FileHandle(File)} — không cần libGDX khởi tạo — và đó mới là
 * chỗ khoá con số ấy lại.
 */
class KageI18nTest {

    @Test
    void shouldRecogniseTheMissingKeyMarker() {
        assertThat(KageI18n.isMissingMarker("???error.abc???", "error.abc")).isTrue();
        assertThat(KageI18n.isMissingMarker(null, "error.abc")).isTrue();
    }

    @Test
    void shouldTreatARealTranslationAsPresent() {
        assertThat(KageI18n.isMissingMarker("Tên này đã có người dùng.", "error.duplicate_character_name"))
                .isFalse();
        // Mốc thật là BA dấu hỏi mỗi bên; hai dấu là một chuỗi bình thường, và dấu hiệu là khớp ĐÚNG cả chuỗi, không
        // phải "có chứa".
        assertThat(KageI18n.isMissingMarker("??error.abc??", "error.abc")).isFalse();
        assertThat(KageI18n.isMissingMarker("???error.abc??? gì đó", "error.abc"))
                .isFalse();
    }

    @Test
    void shouldNotConfuseTheMarkerOfADifferentKey() {
        // Khoá A thiếu, nhưng ta đang hỏi về khoá B: không được đọc thành "B cũng thiếu".
        assertThat(KageI18n.isMissingMarker("???error.a???", "error.b")).isFalse();
    }
}
