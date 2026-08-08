package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link CharacterModelRenderer#resolveModelDirectory} là đúng phần logic thuần cần test ở đây —
 * không cần dựng {@link CharacterModelRenderer} thật (constructor mở {@code ModelBatch}/shader,
 * cần GL context; xem {@code CharacterModelFrameBenchmarkTool} cho công cụ thủ công có GL context
 * thật, RENDERING.md §6.5). Suy thư mục từ tier là phép nối chuỗi thuần, tách được khỏi đồ hoạ.
 *
 * <p>Bug gốc sửa ở đây: {@code MODEL_DIRECTORY} từng hardcode {@code "models/med/"} — LOW/HIGH
 * không bao giờ đọc đúng thư mục dù enum {@link GraphicsTier} đã tồn tại ở phía pipeline
 * ({@code tools:asset-pipeline}). Test này khẳng định cả 3 tier, không chỉ MEDIUM.
 */
class GraphicsTierTest {

    @Test
    void resolveModelDirectory_low() {
        assertThat(CharacterModelRenderer.resolveModelDirectory(GraphicsTier.LOW))
                .isEqualTo("models/low/");
    }

    @Test
    void resolveModelDirectory_medium() {
        assertThat(CharacterModelRenderer.resolveModelDirectory(GraphicsTier.MEDIUM))
                .isEqualTo("models/med/");
    }

    @Test
    void resolveModelDirectory_high() {
        assertThat(CharacterModelRenderer.resolveModelDirectory(GraphicsTier.HIGH))
                .isEqualTo("models/high/");
    }
}
