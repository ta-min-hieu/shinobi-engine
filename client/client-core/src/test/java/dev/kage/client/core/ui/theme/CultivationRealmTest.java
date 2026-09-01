package dev.kage.client.core.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Bảng ánh xạ cấp → cảnh giới của [ADR-038 D2] là một quyết định thiết kế có số cụ thể, nên nó
 * đáng được kiểm bằng chính những con số đó chứ không phải bằng mắt.
 *
 * <p>Điểm dễ sai nhất và cũng là chỗ được kiểm dày nhất: <b>ranh giới</b>. Cấp 18 vẫn là Luyện Khí
 * tầng 9 còn cấp 19 đã sang Trúc Cơ tầng 1 — lệch một cấp ở đây thì mọi mốc mở khoá trong GDD §5
 * (chọn linh căn ở cấp 10 = Luyện Khí tầng 5, Thần Thông ở cấp 50 = Tử Phủ tầng 7) đều mô tả sai.
 */
class CultivationRealmTest {

    @ParameterizedTest(name = "cấp {0} → {1} tầng {2}")
    @CsvSource({
        // Ranh giới DƯỚI, ranh giới TRÊN và một mốc giữa của từng cảnh giới 2-cấp-một-tầng.
        "1,  realm.luyen_khi,   1",
        "2,  realm.luyen_khi,   1",
        "3,  realm.luyen_khi,   2",
        // Mốc GDD §5: chọn linh căn ở cấp 10 — tài liệu gọi đó là Luyện Khí tầng 5.
        "10, realm.luyen_khi,   5",
        "18, realm.luyen_khi,   9",
        "19, realm.truc_co,     1",
        // Mốc GDD §5: bang hội ở cấp 25 — Trúc Cơ tầng 4.
        "25, realm.truc_co,     4",
        "36, realm.truc_co,     9",
        "37, realm.tu_phu,      1",
        // Mốc GDD §5: Thần Thông ở cấp 50 — Tử Phủ tầng 7.
        "50, realm.tu_phu,      7",
        "54, realm.tu_phu,      9",
        "55, realm.kim_dan,     1",
        "72, realm.kim_dan,     9",
        "73, realm.nguyen_anh,  1",
        "90, realm.nguyen_anh,  9",
        // Hoá Thần đổi nhịp: MỘT cấp một tầng.
        "91, realm.hoa_than,    1",
        "92, realm.hoa_than,    2",
        "99, realm.hoa_than,    9",
    })
    void mapsLevelToRealmAndTier(int level, String expectedKey, int expectedTier) {
        CultivationRealm realm = CultivationRealm.of(level);

        assertThat(realm.nameKey()).isEqualTo(expectedKey);
        assertThat(realm.tier()).isEqualTo(expectedTier);
    }

    @Test
    @DisplayName("Cấp 100 là Hoá Thần Viên Mãn — mốc Độ Kiếp, khác mọi cấp còn lại")
    void marksLevel100AsPerfected() {
        CultivationRealm realm = CultivationRealm.of(100);

        assertThat(realm.nameKey()).isEqualTo("realm.hoa_than");
        assertThat(realm.tier()).isEqualTo(9);
        assertThat(realm.perfected()).isTrue();
    }

    @Test
    @DisplayName("Chỉ DUY NHẤT cấp 100 là Viên Mãn — cấp 99 vẫn là tầng 9 thường")
    void doesNotMarkLevel99AsPerfected() {
        assertThat(CultivationRealm.of(99).perfected()).isFalse();
    }

    /**
     * {@code CharacterSummary.getLevel()} trả về {@code 0} khi server chưa đặt trường đó — proto3
     * không phân biệt "chưa đặt" với "bằng 0". Màn hình chọn nhân vật phải hiện được cái gì đó.
     */
    @ParameterizedTest(name = "cấp bất thường {0} vẫn ra cảnh giới thấp nhất")
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void clampsLevelBelowOneToTheFirstTier(int level) {
        CultivationRealm realm = CultivationRealm.of(level);

        assertThat(realm.nameKey()).isEqualTo("realm.luyen_khi");
        assertThat(realm.tier()).isEqualTo(1);
        assertThat(realm.perfected()).isFalse();
    }

    @ParameterizedTest(name = "cấp vượt trần {0} vẫn ra Viên Mãn")
    @ValueSource(ints = {101, 999, Integer.MAX_VALUE})
    void clampsLevelAboveHundredToPerfected(int level) {
        CultivationRealm realm = CultivationRealm.of(level);

        assertThat(realm.nameKey()).isEqualTo("realm.hoa_than");
        assertThat(realm.perfected()).isTrue();
    }

    @Test
    @DisplayName("Mọi cấp 1-100 đều cho tầng trong khoảng 1..9 — không cấp nào rơi ra ngoài bảng")
    void everyLevelYieldsATierWithinNine() {
        for (int level = 1; level <= 100; level++) {
            CultivationRealm realm = CultivationRealm.of(level);
            assertThat(realm.tier()).as("cấp %d", level).isBetween(1, 9);
        }
    }
}
