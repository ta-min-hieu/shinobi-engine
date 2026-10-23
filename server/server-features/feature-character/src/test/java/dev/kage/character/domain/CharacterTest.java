package dev.kage.character.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class CharacterTest {

    @Test
    void shouldAcceptName_whenValidVietnameseCharacters() {
        Character character = new Character(1L, 1L, 1, "KageHero", Faction.HOA, 1, 0, 0, Instant.now());

        assertThat(character.name()).isEqualTo("KageHero");
    }

    @Test
    void shouldRejectName_whenTooShort() {
        assertThatThrownBy(() -> new Character(1L, 1L, 1, "A", Faction.HOA, 1, 0, 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectName_whenTooLong() {
        assertThatThrownBy(() -> new Character(1L, 1L, 1, "A".repeat(17), Faction.HOA, 1, 0, 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectName_whenContainsInvalidCharacter() {
        assertThatThrownBy(() -> new Character(1L, 1L, 1, "Kage!Hero", Faction.HOA, 1, 0, 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectLevel_whenBelowMinimum() {
        assertThatThrownBy(() -> new Character(1L, 1L, 1, "KageHero", Faction.HOA, 0, 0, 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNegativeExp() {
        assertThatThrownBy(() -> new Character(1L, 1L, 1, "KageHero", Faction.HOA, 1, -1, 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validateNameShouldMatchCompactConstructorRules() {
        assertThatThrownBy(() -> Character.validateName("A")).isInstanceOf(IllegalArgumentException.class);
    }

    /** Bản không ném phải nói đúng điều {@code validateName} nói — kể cả với U+0000 và null. */
    @Test
    void isValidNameShouldAgreeWithValidateName() {
        assertThat(Character.isValidName("KageHero")).isTrue();
        assertThat(Character.isValidName("Nguyên_Anh")).isTrue();
        assertThat(Character.isValidName("A")).isFalse();
        assertThat(Character.isValidName("A".repeat(17))).isFalse();
        assertThat(Character.isValidName("Kage\0Hero")).isFalse();
        assertThat(Character.isValidName("Kage Hero")).isFalse();
        assertThat(Character.isValidName(null)).isFalse();
    }

    /** Dòng 42 (lô 18): trộn Latin với Cyrillic/Hy Lạp bị chặn; tên thuần một hệ và tiếng Việt vẫn qua. */
    @Test
    void validateNewNameShouldRejectLookalikeScriptMixes_butKeepSingleScriptNames() {
        assertThatThrownBy(() -> Character.validateNewName("Kаge"))
                .as("а Cyrillic giả a Latin")
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Character.validateNewName("Kagο"))
                .as("ο Hy Lạp giả o Latin")
                .isInstanceOf(IllegalArgumentException.class);
        Character.validateNewName("Каге");
        Character.validateNewName("Καγε");
        Character.validateNewName("Kiếm_Tiên9");
        Character.validateNewName("Kage龍");
    }

    /**
     * Luật tên MỚI (quyết định chủ dự án 2026-10-11): ký tự lấp chỗ Hangul là chữ cái theo Unicode
     * nhưng hiện ra là khoảng trống — tên vô hình; chữ khổ toàn phần và chữ toán học trông y hệt chữ
     * thường — giả mạo được tên người khác. Dựng bằng mã điểm để mẫu thử nhìn thấy được trong mã.
     */
    @Test
    void validateNewNameShouldRejectInvisibleAndCompatibilityCharacters() {
        for (int filler : new int[] {0x115F, 0x1160, 0x3164, 0xFFA0}) {
            String invisible = "A" + java.lang.Character.toString(filler) + "B";
            assertThatThrownBy(() -> Character.validateNewName(invisible))
                    .as("U+%04X", filler)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        String fullWidth = java.lang.Character.toString(0xFF2B) + java.lang.Character.toString(0xFF21);
        String mathBold = java.lang.Character.toString(0x1D40A) + java.lang.Character.toString(0x1D41A);
        assertThatThrownBy(() -> Character.validateNewName(fullWidth)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Character.validateNewName(mathBold)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Character.validateNewName("A")).isInstanceOf(IllegalArgumentException.class);

        Character.validateNewName("KageHero");
        Character.validateNewName("Nguyên_Anh");
        Character.validateNewName("天剑宗");
    }

    /**
     * Nhân vật tạo TRƯỚC luật mới có thể đang mang tên nay bị cấm; đọc chúng lên từ DB không được nổ —
     * nếu không, cả tài khoản không liệt kê nổi nhân vật. Luật mới chỉ áp lúc tạo.
     */
    @Test
    void shouldStillBuildAnExistingCharacter_whoseNameTheNewRuleWouldRefuse() {
        String legacyName = java.lang.Character.toString(0xFF2B) + java.lang.Character.toString(0xFF21);

        Character legacy = new Character(1L, 1L, 1, legacyName, Faction.HOA, 1, 0, 0, Instant.now());

        assertThat(legacy.name()).isEqualTo(legacyName);
        assertThat(Character.isValidName(legacyName))
                .as("tra theo tên vẫn phải tìm được nhân vật cũ")
                .isTrue();
    }

    /**
     * Thông điệp từ chối tên được gateway ghi ra log ở mỗi lượt tạo nhân vật sai — không được chép
     * nguyên văn đầu vào của client (xem {@code AccountTest} cùng tên: khuếch đại log, dòng log giả).
     */
    @Test
    void shouldKeepRejectionMessageBounded_andFreeOfClientControlCharacters() {
        assertThatThrownBy(() -> Character.validateName("A".repeat(1_000_000)))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).hasSizeLessThan(200));
        assertThatThrownBy(() -> Character.validateName("Ab\nINFO forged"))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("\n").doesNotContain("forged"));
        assertThatThrownBy(() -> Character.validateName("Kage\u202EHero"))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(
                        e -> assertThat(e.getMessage()).doesNotContain("\u202E").contains("U+202E"));
    }
}
