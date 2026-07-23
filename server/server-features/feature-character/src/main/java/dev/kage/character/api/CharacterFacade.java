package dev.kage.character.api;

import dev.kage.character.application.CreateCharacterUseCase;
import dev.kage.character.application.ListCharactersUseCase;
import dev.kage.character.application.SelectCharacterUseCase;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.CharacterStats;
import dev.kage.character.domain.PositionSnapshot;
import java.util.List;
import java.util.Optional;

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

    public CharacterStats loadStats(long characterId) {
        return characterPort.loadStats(characterId);
    }

    /** Tra nhân vật theo tên — dùng cho kết bạn/gửi thư theo tên (feature-friend, feature-mail). */
    public Optional<Character> findByName(String name) {
        return characterPort.findByName(name);
    }

    /** Tra nhân vật theo ID — dùng để hiển thị tên (danh sách bạn bè, người gửi thư/chat riêng). */
    public Optional<Character> findById(long characterId) {
        return characterPort.findById(characterId);
    }
}
