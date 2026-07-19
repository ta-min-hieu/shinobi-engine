package dev.kage.character.api;

import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.PositionSnapshot;
import java.util.List;

/**
 * API công khai của {@code feature-character} — module khác (server-gateway) CHỈ gọi qua đây,
 * không bao giờ đọc bảng {@code character} trực tiếp (Điều 24).
 */
public final class CharacterFacade {

    private final CreateCharacterUseCase createCharacterUseCase;
    private final ListCharactersUseCase listCharactersUseCase;
    private final SelectCharacterUseCase selectCharacterUseCase;
    // loadPosition/savePosition không có nghiệp vụ nào ngoài delegation thuần (characterId đã
    // được validate từ session ở gateway, không cần UseCase riêng chỉ để gọi thẳng Port — 3
    // UseCase còn lại có validate/orchestrate thật, đây thì không).
    private final CharacterPort characterPort;

    public CharacterFacade(
            CreateCharacterUseCase createCharacterUseCase,
            ListCharactersUseCase listCharactersUseCase,
            SelectCharacterUseCase selectCharacterUseCase,
            CharacterPort characterPort) {
        this.createCharacterUseCase = createCharacterUseCase;
        this.listCharactersUseCase = listCharactersUseCase;
        this.selectCharacterUseCase = selectCharacterUseCase;
        this.characterPort = characterPort;
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

    public PositionSnapshot loadPosition(long characterId) {
        return characterPort.loadPosition(characterId);
    }

    public void savePosition(long characterId, float x, float y) {
        characterPort.savePosition(characterId, x, y);
    }
}
