package dev.kage.character.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.character.domain.Transaction;

class SelectCharacterUseCaseTest {

    static final class FakeCharacterPort implements CharacterPort {
        final List<Character> characters = new ArrayList<>();
        final AtomicLong nextId = new AtomicLong(1);

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
            Character character = new Character(
                    nextId.getAndIncrement(),
                    newCharacter.accountId(),
                    1,
                    newCharacter.name(),
                    newCharacter.faction(),
                    1,
                    0,
                    0,
                    Instant.now());
            characters.add(character);
            return character;
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
    void shouldReturnCharacter_whenOwnedByAccount() {
        FakeCharacterPort port = new FakeCharacterPort();
        Character created = port.create(null, new NewCharacter(1L, "KageHero", Faction.HOA, "idem-1"));
        SelectCharacterUseCase useCase = new SelectCharacterUseCase(port);

        Character selected = useCase.execute(1L, created.id());

        assertThat(selected).isEqualTo(created);
    }

    @Test
    void shouldReject_whenCharacterBelongsToDifferentAccount() {
        FakeCharacterPort port = new FakeCharacterPort();
        Character created = port.create(null, new NewCharacter(1L, "KageHero", Faction.HOA, "idem-1"));
        SelectCharacterUseCase useCase = new SelectCharacterUseCase(port);

        // accountId=2 cố chọn nhân vật của accountId=1 — đúng kịch bản IDOR SECURITY.md §4 mô tả.
        assertThatThrownBy(() -> useCase.execute(2L, created.id())).isInstanceOf(CharacterNotFoundException.class);
    }

    @Test
    void shouldReject_whenCharacterDoesNotExist() {
        FakeCharacterPort port = new FakeCharacterPort();
        SelectCharacterUseCase useCase = new SelectCharacterUseCase(port);

        assertThatThrownBy(() -> useCase.execute(1L, 999L)).isInstanceOf(CharacterNotFoundException.class);
    }
}
