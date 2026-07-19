package dev.kage.character.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.Transaction;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListCharactersUseCaseTest {

    static final class FakeCharacterPort implements CharacterPort {
        private final List<Character> characters;

        FakeCharacterPort(List<Character> characters) {
            this.characters = characters;
        }

        @Override
        public List<Character> listByAccount(long accountId) {
            return characters.stream().filter(c -> c.accountId() == accountId).toList();
        }

        @Override
        public int countByAccount(long accountId) {
            return listByAccount(accountId).size();
        }

        @Override
        public boolean existsByName(String name) {
            return characters.stream().anyMatch(c -> c.name().equals(name));
        }

        @Override
        public Character create(Transaction tx, NewCharacter newCharacter) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }
    }

    @Test
    void shouldReturnOnlyCharactersOfGivenAccount() {
        var characterOfAccount1 = new Character(1L, 1L, 1, "HeroOne", Faction.HOA, 1, 0, 0, Instant.now());
        var characterOfAccount2 = new Character(2L, 2L, 1, "HeroTwo", Faction.HOA, 1, 0, 0, Instant.now());
        var port = new FakeCharacterPort(List.of(characterOfAccount1, characterOfAccount2));
        var useCase = new ListCharactersUseCase(port);

        var result = useCase.execute(1L);

        assertThat(result).containsExactly(characterOfAccount1);
    }

    @Test
    void shouldReturnEmptyList_whenAccountHasNoCharacters() {
        var port = new FakeCharacterPort(List.of());
        var useCase = new ListCharactersUseCase(port);

        assertThat(useCase.execute(1L)).isEmpty();
    }
}
