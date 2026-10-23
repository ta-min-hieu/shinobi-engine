package dev.kage.character.application;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.CharacterPort;
import dev.kage.character.domain.Faction;
import dev.kage.character.domain.FactionChangePolicy;
import dev.kage.character.domain.FactionChangePort;
import dev.kage.character.domain.FactionChangeState;
import dev.kage.character.domain.TransactionRunner;

/**
 * Chọn/đổi linh căn khi nhân vật đủ Lv10 (GDD §4). {@code characterId} do gateway truyền vào SAU KHI đã
 * verify sở hữu qua session (đã bound lúc {@code SelectCharacterRequest}, Điều 24) — UseCase này không
 * nhận {@code accountId}.
 *
 * <p>ADR-066: lần đầu (thức tỉnh) miễn phí, lần thứ hai miễn phí nếu dưới Lv30, sau đó mỗi lần tốn một Tẩy
 * Căn Đan ({@link FactionChangePolicy}). Đếm + tiêu đan + ghi hệ mới trong MỘT transaction, dưới khoá túi
 * đồ rồi khoá dòng nhân vật — hai lệnh đổi song song không cùng hưởng một lần miễn phí.
 */
public final class SelectFactionUseCase {

    static final int MIN_LEVEL = FactionChangePolicy.MIN_LEVEL;

    private final CharacterPort characterPort;
    private final FactionChangePort changePort;
    private final TransactionRunner transactionRunner;

    /** Không có túi đồ/đan — bộ đếm trong bộ nhớ (composition test). */
    public SelectFactionUseCase(CharacterPort characterPort) {
        this(characterPort, FactionChangePort.inMemory(characterPort), action -> action.accept(null));
    }

    public SelectFactionUseCase(
            CharacterPort characterPort, FactionChangePort changePort, TransactionRunner transactionRunner) {
        this.characterPort = characterPort;
        this.changePort = changePort;
        this.transactionRunner = transactionRunner;
    }

    public Character execute(long characterId, Faction faction) {
        Character character =
                characterPort.findById(characterId).orElseThrow(() -> new CharacterNotFoundException(characterId));
        if (character.level() < MIN_LEVEL) {
            throw new FactionSelectionLevelTooLowException(characterId, character.level(), MIN_LEVEL);
        }
        if (character.faction() == faction) {
            throw new FactionUnchangedException(characterId);
        }
        transactionRunner.inTransaction(tx -> {
            changePort.lockInventory(tx, characterId);
            FactionChangeState state = changePort.lockState(tx, characterId);
            // Kiểm LẠI dưới khoá: lệnh đổi song song có thể vừa ghi đúng hệ này (bấm đúp) — không trả đan cho nó.
            if (state.faction() == faction) {
                throw new FactionUnchangedException(characterId);
            }
            boolean free = FactionChangePolicy.isFree(state.changes(), character.level());
            if (!free && !changePort.takePill(tx, characterId)) {
                throw new FactionChangePillRequiredException(characterId);
            }
            changePort.recordChange(tx, characterId, faction, !free);
        });
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
