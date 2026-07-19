package dev.kage.character.api;

import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.character.domain.Character;
import java.util.List;

/**
 * API công khai của {@code feature-character} — module khác (server-gateway) CHỈ gọi qua đây,
 * không bao giờ đọc bảng {@code character} trực tiếp (Điều 24).
 */
public final class CharacterFacade {

    private final CreateCharacterUseCase createCharacterUseCase;
    private final ListCharactersUseCase listCharactersUseCase;
    private final SelectCharacterUseCase selectCharacterUseCase;

    public CharacterFacade(
            CreateCharacterUseCase createCharacterUseCase,
            ListCharactersUseCase listCharactersUseCase,
            SelectCharacterUseCase selectCharacterUseCase) {
        this.createCharacterUseCase = createCharacterUseCase;
        this.listCharactersUseCase = listCharactersUseCase;
        this.selectCharacterUseCase = selectCharacterUseCase;
    }

    public Character createCharacter(long accountId, String name, String idempotencyKey) {
        return createCharacterUseCase.execute(accountId, name, idempotencyKey);
    }

    public List<Character> listCharacters(long accountId) {
        return listCharactersUseCase.execute(accountId);
    }

    public Character selectCharacter(long accountId, long characterId) {
        return selectCharacterUseCase.execute(accountId, characterId);
    }
}
