package dev.kage.character.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.character.domain.Transaction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
        public Optional<Character> findByName(String name) {
            return characters.stream().filter(c -> c.name().equals(name)).findFirst();
        }

        @Override
        public Optional<Character> findById(long characterId) {
            return characters.stream().filter(c -> c.id() == characterId).findFirst();
        }

        @Override
        public Character create(Transaction tx, NewCharacter newCharacter) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public PositionSnapshot loadPosition(long characterId) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public void savePosition(long characterId, float x, float y) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public CharacterStats loadStats(long characterId) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public void updateFaction(long characterId, Faction faction) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }
    }

    @Test
    void shouldReturnOnlyCharactersOfGivenAccount() {
        Character characterOfAccount1 = new Character(1L, 1L, 1, "HeroOne", Faction.HOA, 1, 0, 0, Instant.now());
        Character characterOfAccount2 = new Character(2L, 2L, 1, "HeroTwo", Faction.HOA, 1, 0, 0, Instant.now());
        FakeCharacterPort port = new FakeCharacterPort(List.of(characterOfAccount1, characterOfAccount2));
        ListCharactersUseCase useCase = new ListCharactersUseCase(port);

        List<Character> result = useCase.execute(1L);

        assertThat(result).containsExactly(characterOfAccount1);
    }

    @Test
    void shouldReturnEmptyList_whenAccountHasNoCharacters() {
        FakeCharacterPort port = new FakeCharacterPort(List.of());
        ListCharactersUseCase useCase = new ListCharactersUseCase(port);

        assertThat(useCase.execute(1L)).isEmpty();
    }
}
