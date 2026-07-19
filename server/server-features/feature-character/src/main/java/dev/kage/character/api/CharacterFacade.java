package dev.kage.character.api;

import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.domain.Character;
import java.util.List;

/**
 * API công khai của {@code feature-character} — module khác (server-gateway) CHỈ gọi qua đây,
 * không bao giờ đọc bảng {@code character} trực tiếp (Điều 24).
 */
public final class CharacterFacade {

    private final CreateCharacterUseCase createCharacterUseCase;
    private final ListCharactersUseCase listCharactersUseCase;

    public CharacterFacade(CreateCharacterUseCase createCharacterUseCase, ListCharactersUseCase listCharactersUseCase) {
        this.createCharacterUseCase = createCharacterUseCase;
        this.listCharactersUseCase = listCharactersUseCase;
    }

    public Character createCharacter(long accountId, String name, String idempotencyKey) {
        return createCharacterUseCase.execute(accountId, name, idempotencyKey);
    }

    public List<Character> listCharacters(long accountId) {
        return listCharactersUseCase.execute(accountId);
    }
}
