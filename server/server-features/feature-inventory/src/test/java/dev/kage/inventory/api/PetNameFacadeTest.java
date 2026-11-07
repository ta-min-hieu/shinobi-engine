package dev.kage.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.inventory.domain.PetNamePort;

/** ADR-193 — luật tên linh thú và các nhánh kết quả. */
final class PetNameFacadeTest {

    private static final long OWNER = 1L;
    private static final long NO_PET_OWNER = 2L;

    private final Map<Long, String> names = new HashMap<>();
    private final PetNameFacade facade = new PetNameFacade(new PetNamePort() {
        @Override
        public boolean renameEquipped(long characterId, String name) {
            if (characterId != OWNER) {
                return false;
            }
            names.put(characterId, name);
            return true;
        }

        @Override
        public Optional<String> equippedName(long characterId) {
            return Optional.ofNullable(names.get(characterId)).filter(n -> !n.isEmpty());
        }
    });

    @Test
    void renamesClearsAndRefuses() {
        assertThat(facade.rename(OWNER, "Tiểu Hồ")).isEqualTo(PetNameFacade.Outcome.OK);
        assertThat(facade.equippedName(OWNER)).contains("Tiểu Hồ");
        assertThat(facade.rename(OWNER, "")).isEqualTo(PetNameFacade.Outcome.CLEARED);
        assertThat(facade.equippedName(OWNER)).isEmpty();
        assertThat(facade.rename(NO_PET_OWNER, "Hồ Ly")).isEqualTo(PetNameFacade.Outcome.NO_PET);
    }

    @Test
    void enforcesTheNameRule() {
        assertThat(PetNameFacade.isValid("A")).isFalse();
        assertThat(PetNameFacade.isValid("Mười Ba Ký Tự X")).isFalse();
        assertThat(PetNameFacade.isValid("Hai  Cách")).isFalse();
        assertThat(PetNameFacade.isValid("Ly<script>")).isFalse();
        assertThat(PetNameFacade.isValid("Huyền Quy 9")).isTrue();
        assertThat(facade.rename(OWNER, "x")).isEqualTo(PetNameFacade.Outcome.INVALID_NAME);
    }
}
