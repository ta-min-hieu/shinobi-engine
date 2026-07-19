package dev.kage.character.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.Transaction;
import dev.kage.character.domain.TransactionRunner;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CreateCharacterUseCaseTest {

    /** Fake = implementation in-memory THẬT, phát hiện lỗi tương tác thật hơn Mockito (TESTING §4). */
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
        public Character create(Transaction tx, NewCharacter newCharacter) {
            var character = new Character(
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
    }

    static final TransactionRunner DIRECT_TX = action -> action.accept(null);

    @Test
    void shouldCreateCharacter_whenValidAndUnderLimit() {
        var port = new FakeCharacterPort();
        var useCase = new CreateCharacterUseCase(port, DIRECT_TX);

        var character = useCase.execute(1L, "KageHero", "idem-1");

        assertThat(character.name()).isEqualTo("KageHero");
        assertThat(character.faction()).isEqualTo(Faction.HOA);
        assertThat(port.characters).hasSize(1);
    }

    @Test
    void shouldRejectDuplicateName() {
        var port = new FakeCharacterPort();
        var useCase = new CreateCharacterUseCase(port, DIRECT_TX);
        useCase.execute(1L, "KageHero", "idem-1");

        assertThatThrownBy(() -> useCase.execute(2L, "KageHero", "idem-2"))
                .isInstanceOf(DuplicateCharacterNameException.class);
        assertThat(port.characters).hasSize(1);
    }

    @Test
    void shouldRejectWhenAccountReachedCharacterLimit() {
        var port = new FakeCharacterPort();
        var useCase = new CreateCharacterUseCase(port, DIRECT_TX);
        for (int i = 0; i < CreateCharacterUseCase.MAX_CHARACTERS_PER_ACCOUNT; i++) {
            useCase.execute(1L, "Hero" + i, "idem-" + i);
        }

        assertThatThrownBy(() -> useCase.execute(1L, "OneMore", "idem-extra"))
                .isInstanceOf(CharacterLimitExceededException.class);
        assertThat(port.characters).hasSize(CreateCharacterUseCase.MAX_CHARACTERS_PER_ACCOUNT);
    }

    @Test
    void shouldRejectInvalidName_beforeTouchingPort() {
        var port = new FakeCharacterPort();
        var useCase = new CreateCharacterUseCase(port, DIRECT_TX);

        assertThatThrownBy(() -> useCase.execute(1L, "A", "idem-1")).isInstanceOf(IllegalArgumentException.class);
        assertThat(port.characters).isEmpty();
    }
}
