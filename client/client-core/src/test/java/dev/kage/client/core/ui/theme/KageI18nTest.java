package dev.kage.client.core.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@code I18NBundle} báo khoá thiếu bằng GIÁ TRỊ TRẢ VỀ, không bằng exception.
 *
 * <p>{@link KageI18n#load()} gọi {@code setExceptionOnMissingKey(false)} để một khoá thiếu không
 * làm sập UI. Hệ quả dễ viết sai: một {@code try/catch (MissingResourceException)} quanh
 * {@code bundle.get} trông như đang phòng thủ mà **không bao giờ chạy**, và
 * {@code ??error.abc??} hiện thẳng lên màn hình người chơi. Bản đầu của chính lần sửa 2026-09-19
 * viết đúng kiểu đó, nên chỗ nhận biết được tách ra và đo riêng.
 *
 * <p>Đo hàm thuần chứ không dựng bundle thật: {@code I18NBundle.createBundle} cần
 * {@code Gdx.files}, tức cần ngữ cảnh libGDX — cùng lý do {@code ConnectionCoordinator}
 * tách {@code plausibleRoundTrip} ra thành hàm tĩnh.
 */
class KageI18nTest {

    @Test
    void shouldRecogniseTheMissingKeyMarker() {
        assertThat(KageI18n.isMissingMarker("??error.abc??", "error.abc")).isTrue();
        assertThat(KageI18n.isMissingMarker(null, "error.abc")).isTrue();
    }

    @Test
    void shouldTreatARealTranslationAsPresent() {
        assertThat(KageI18n.isMissingMarker("Tên này đã có người dùng.", "error.duplicate_character_name"))
                .isFalse();
        // Bản dịch TÌNH CỜ chứa dấu ?? vẫn là bản dịch thật — dấu hiệu là khớp ĐÚNG cả chuỗi, không
        // phải "có chứa".
        assertThat(KageI18n.isMissingMarker("??", "error.abc")).isFalse();
        assertThat(KageI18n.isMissingMarker("??error.abc?? gì đó", "error.abc")).isFalse();
    }

    @Test
    void shouldNotConfuseTheMarkerOfADifferentKey() {
        // Khoá A thiếu, nhưng ta đang hỏi về khoá B: không được đọc thành "B cũng thiếu".
        assertThat(KageI18n.isMissingMarker("??error.a??", "error.b")).isFalse();
    }
}
