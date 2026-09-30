package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link MonsterModelKey} là phép biến đổi chuỗi thuần — test được thẳng, không cần GL, cùng lý do
 * {@link GraphicsTierTest} test {@code resolveModelDirectory} tách khỏi {@link WorldModelRenderer}.
 *
 * <p>Giá trị thật của bộ test này nằm ở chỗ khoá sinh ra phải khớp ĐÚNG tên file trên đĩa: sai một
 * dấu {@code _}/{@code -} thì {@code addMonsterOrNpc} chỉ lặng lẽ vẽ hộp và không có gì báo hỏng.
 */
class MonsterModelKeyTest {

    private final Locale defaultLocale = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(defaultLocale);
    }

    @Test
    void mapsTheOnlyMonsterThatHasArt() {
        // Khớp file thật assets/exported/models/<tier>/mob_training-dummy.glb (ASSET-LOG.md).
        assertThat(MonsterModelKey.of("training_dummy")).isEqualTo("mob_training-dummy");
    }

    @Test
    void mapsMonsterCodeThatHasNoArtYet() {
        // sample_boss sống KHÔNG có art từ 2026-08-09 tới 2026-09-30 — suốt quãng đó khoá vẫn phải hợp
        // lệ và đoán được, việc "chưa có file" là chuyện của WorldModelRenderer.addMonsterOrNpc, không
        // phải của lớp này. Giữ mã này làm ca thử: lớp này không biết (và không được biết) con nào có art.
        assertThat(MonsterModelKey.of("sample_boss")).isEqualTo("mob_sample-boss");
    }

    @Test
    void keepsCodeWithoutUnderscoreIntact() {
        assertThat(MonsterModelKey.of("slime")).isEqualTo("mob_slime");
    }

    @Test
    void isNotAffectedByTheMachineLocale() {
        // Tiếng Thổ Nhĩ Kỳ: 'I' viết thường thành 'ı' (i không chấm) chứ không phải 'i'. Không ghim
        // Locale.ROOT thì máy đặt locale này sinh ra "mob_traınıng-dummy" — một khoá không khớp file
        // nào, và triệu chứng là "quái thành hộp đỏ ở đúng máy đó" chứ không phải một lỗi rõ ràng.
        Locale.setDefault(Locale.forLanguageTag("tr"));
        assertThat(MonsterModelKey.of("TRAINING_DUMMY")).isEqualTo("mob_training-dummy");
    }
}
