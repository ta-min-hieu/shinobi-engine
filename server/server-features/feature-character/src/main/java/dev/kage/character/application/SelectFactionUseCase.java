package dev.kage.character.application;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.Faction;

/**
 * Chọn/đổi hệ phái khi nhân vật đủ Lv10 (GDD §4 "chọn hệ ở Lv10", Phase 8 4 hệ phái mới).
 * {@code characterId} do gateway truyền vào SAU KHI đã verify sở hữu qua session (đã bound lúc
 * {@code SelectCharacterRequest}, Điều 24) — UseCase này không nhận {@code accountId}.
 *
 * <p>Scope cut có chủ đích (cùng tinh thần feature-character/CLAUDE.md "Quyết định MVP"): GDD nói
 * "đổi 1 lần miễn phí trước Lv30, sau đó tốn Đá Chuyển Hệ" — lát cắt này CHỈ gate {@code level>=10},
 * CHƯA đếm số lần đổi/enforce tốn item (cần thêm cột DB {@code faction_change_count} + item "Đá
 * Chuyển Hệ" ở feature-inventory, để dành lát cắt sau).
 */
public final class SelectFactionUseCase {

    static final int MIN_LEVEL = 10;

    private final CharacterPort characterPort;

    public SelectFactionUseCase(CharacterPort characterPort) {
        this.characterPort = characterPort;
    }

    public Character execute(long characterId, Faction faction) {
        Character character =
                characterPort.findById(characterId).orElseThrow(() -> new CharacterNotFoundException(characterId));
        if (character.level() < MIN_LEVEL) {
            throw new FactionSelectionLevelTooLowException(characterId, character.level(), MIN_LEVEL);
        }
        characterPort.updateFaction(characterId, faction);
        return new Character(
                character.id(),
                character.accountId(),
                character.realmId(),
                character.name(),
                faction,
                character.level(),
                character.exp(),
                character.rebirthCount(),
                character.createdAt());
    }
}
