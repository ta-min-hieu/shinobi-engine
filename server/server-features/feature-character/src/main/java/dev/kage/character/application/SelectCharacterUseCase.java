package dev.kage.character.application;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;

/**
 * Chọn nhân vật để vào world — verify nhân vật thuộc account đã đăng nhập (SECURITY.md §4, chống
 * IDOR). Tái dùng {@link CharacterPort#listByAccount} đã có, không thêm Port method mới — tối đa
 * {@link CreateCharacterUseCase#MAX_CHARACTERS_PER_ACCOUNT} nhân vật/account nên lọc trong bộ nhớ
 * rẻ hơn thêm 1 query SQL (Điều 9 — YAGNI).
 */
public final class SelectCharacterUseCase {

    private final CharacterPort characterPort;

    public SelectCharacterUseCase(CharacterPort characterPort) {
        this.characterPort = characterPort;
    }

    public Character execute(long accountId, long characterId) {
        return characterPort.listByAccount(accountId).stream()
                .filter(c -> c.id() == characterId)
                .findFirst()
                .orElseThrow(() -> new CharacterNotFoundException(characterId));
    }
}
