package dev.kage.character.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.NewCharacter;
import dev.kage.character.domain.PositionSnapshot;
import dev.kage.character.domain.Transaction;

/** Phase 8 (4 hệ phái) — GDD §4 "chọn hệ ở Lv10". Fake = implementation in-memory thật (TESTING §4). */
class SelectFactionUseCaseTest {

    static final class FakeCharacterPort implements CharacterPort {
        final List<Character> characters = new ArrayList<>();

        FakeCharacterPort(Character... seed) {
            characters.addAll(List.of(seed));
        }

        @Override
        public List<Character> listByAccount(long accountId) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public int countByAccount(long accountId) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public boolean existsByName(String name) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public Optional<Character> findByName(String name) {
            throw new UnsupportedOperationException("không dùng trong test này");
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
        public long addExperience(long characterId, long amount) {
            throw new UnsupportedOperationException("không dùng trong test này");
        }

        @Override
        public void saveLevel(long characterId, int level) {
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

    private static Character characterAtLevel(int level) {
        return new Character(1L, 1L, 1, "HeroOne", Faction.HOA, level, 0, 0, Instant.now());
    }

    @Test
    void shouldUpdateFaction_whenLevelAtLeastTen() {
        FakeCharacterPort port = new FakeCharacterPort(characterAtLevel(10));
        SelectFactionUseCase useCase = new SelectFactionUseCase(port);

        Character result = useCase.execute(1L, Faction.THUY);

        assertThat(result.faction()).isEqualTo(Faction.THUY);
        assertThat(port.findById(1L).orElseThrow().faction()).isEqualTo(Faction.THUY);
    }

    @Test
    void shouldRejectFactionChange_whenLevelBelowTen() {
        FakeCharacterPort port = new FakeCharacterPort(characterAtLevel(9));
        SelectFactionUseCase useCase = new SelectFactionUseCase(port);

        assertThatThrownBy(() -> useCase.execute(1L, Faction.THUY))
                .isInstanceOf(FactionSelectionLevelTooLowException.class);
        assertThat(port.findById(1L).orElseThrow().faction()).isEqualTo(Faction.HOA); // không đổi gì
    }

    @Test
    void shouldThrowNotFound_whenCharacterDoesNotExist() {
        FakeCharacterPort port = new FakeCharacterPort();
        SelectFactionUseCase useCase = new SelectFactionUseCase(port);

        assertThatThrownBy(() -> useCase.execute(999L, Faction.THUY)).isInstanceOf(CharacterNotFoundException.class);
    }
}
