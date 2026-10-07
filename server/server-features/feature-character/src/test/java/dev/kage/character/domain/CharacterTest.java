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
