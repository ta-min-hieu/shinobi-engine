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
}
