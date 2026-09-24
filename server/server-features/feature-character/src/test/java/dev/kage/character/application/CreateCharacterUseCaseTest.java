package dev.kage.character.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import dev.kage.character.domain.TransactionRunner;

class CreateCharacterUseCaseTest {

    /** Fake = implementation in-memory THẬT, phát hiện lỗi tương tác thật hơn Mockito (TESTING §4). */
    static final class FakeCharacterPort implements CharacterPort {
        final List<Character> characters = new ArrayList<>();
        final AtomicLong nextId = new AtomicLong(1);
        final Map<Long, PositionSnapshot> positions = new HashMap<>();

        @Override
        public PositionSnapshot loadPosition(long characterId) {
            return positions.getOrDefault(characterId, new PositionSnapshot(0f, 0f));
        }

        @Override
        public void savePosition(long characterId, float x, float y) {
            positions.put(characterId, new PositionSnapshot(x, y));
        }

        @Override
        public CharacterStats loadStats(long characterId) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public long loadGold(long characterId) {
            throw new UnsupportedOperationException("không dùng trong test này");
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
        public long addExperience(long characterId, long amount) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public boolean saveLevel(long characterId, int level, long derivedFromExp) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public void updateFaction(long characterId, Faction faction) {
            for (int i = 0; i < characters.size(); i++) {
                Character c = characters.get(i);
                if (c.id() == characterId) {
                    characters.set(
                            i,
                            new Character(
                                    c.id(),
                                    c.accountId(),
                                    c.realmId(),
                                    c.name(),
                                    faction,
                                    c.level(),
                                    c.exp(),
                                    c.rebirthCount(),
                                    c.createdAt()));
                    return;
                }
            }
        }
    }

    static final TransactionRunner DIRECT_TX = action -> action.accept(null);

    @Test
    void shouldCreateCharacter_whenValidAndUnderLimit() {
        FakeCharacterPort port = new FakeCharacterPort();
        CreateCharacterUseCase useCase = new CreateCharacterUseCase(port, DIRECT_TX);

        Character character = useCase.execute(1L, "KageHero", "idem-1");

        assertThat(character.name()).isEqualTo("KageHero");
        assertThat(character.faction()).isEqualTo(Faction.HOA);
        assertThat(port.characters).hasSize(1);
    }

    @Test
    void shouldRejectDuplicateName() {
        FakeCharacterPort port = new FakeCharacterPort();
        CreateCharacterUseCase useCase = new CreateCharacterUseCase(port, DIRECT_TX);
        useCase.execute(1L, "KageHero", "idem-1");

        assertThatThrownBy(() -> useCase.execute(2L, "KageHero", "idem-2"))
                .isInstanceOf(DuplicateCharacterNameException.class);
        assertThat(port.characters).hasSize(1);
    }

    @Test
    void shouldRejectWhenAccountReachedCharacterLimit() {
        FakeCharacterPort port = new FakeCharacterPort();
        CreateCharacterUseCase useCase = new CreateCharacterUseCase(port, DIRECT_TX);
        for (int i = 0; i < CreateCharacterUseCase.MAX_CHARACTERS_PER_ACCOUNT; i++) {
            useCase.execute(1L, "Hero" + i, "idem-" + i);
        }

        assertThatThrownBy(() -> useCase.execute(1L, "OneMore", "idem-extra"))
                .isInstanceOf(CharacterLimitExceededException.class);
        assertThat(port.characters).hasSize(CreateCharacterUseCase.MAX_CHARACTERS_PER_ACCOUNT);
    }

    @Test
    void shouldRejectInvalidName_beforeTouchingPort() {
        FakeCharacterPort port = new FakeCharacterPort();
        CreateCharacterUseCase useCase = new CreateCharacterUseCase(port, DIRECT_TX);

        assertThatThrownBy(() -> useCase.execute(1L, "A", "idem-1")).isInstanceOf(IllegalArgumentException.class);
        assertThat(port.characters).isEmpty();
    }
}
