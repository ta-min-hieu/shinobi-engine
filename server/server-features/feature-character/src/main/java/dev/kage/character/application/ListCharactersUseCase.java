package dev.kage.character.application;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import java.util.List;

/** Liệt kê nhân vật của account — accountId luôn từ session, không từ packet (Điều 24). */
public final class ListCharactersUseCase {

    private final CharacterPort characterPort;

    public ListCharactersUseCase(CharacterPort characterPort) {
        this.characterPort = characterPort;
    }

    public List<Character> execute(long accountId) {
        return characterPort.listByAccount(accountId);
    }
}
