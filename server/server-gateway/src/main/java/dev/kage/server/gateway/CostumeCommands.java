package dev.kage.server.gateway;

import java.time.Clock;
import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.api.CostumeFacade;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.CostumePort;
import dev.kage.protocol.CostumeTier;
import dev.kage.protocol.SetCostumeCommand;
import dev.kage.protocol.SetCostumeOutcome;
import dev.kage.protocol.SetCostumeResponse;

/**
 * Huyễn hình trang phục (ADR-166) — server kiểm bậc đã mở theo cảnh giới, trừ phí, rồi dựng lại ngoại hình qua đúng
 * đường lên cấp ({@link CostumeTierHooks}) để người đang nhìn thấy nhận gói đổi ngoại hình (ADR-045).
 */
final class CostumeCommands {

    private final CostumeFacade costumes;
    private final CharacterFacade characters;
    private final CharacterLevelSync.LevelListener appearanceRefresh;
    private final Clock clock;

    CostumeCommands(
            CostumeFacade costumes,
            CharacterFacade characters,
            CharacterLevelSync.LevelListener appearanceRefresh,
            Clock clock) {
        this.costumes = costumes;
        this.characters = characters;
        this.appearanceRefresh = appearanceRefresh;
        this.clock = clock;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    SetCostumeResponse set(long characterId, SetCostumeCommand command) {
        Optional<Character> character = characters.findById(characterId);
        int level = character.map(Character::level).orElse(1);
        CostumeTier unlocked = CharacterAppearance.tierFor(level);
        int requested = command.getTierValue();
        if (requested < 0 || requested > unlocked.getNumber()) {
            return response(SetCostumeOutcome.SET_COSTUME_OUTCOME_TIER_LOCKED, characterId, unlocked, 0L);
        }
        // Chọn đúng bậc đang mở = trở về theo cảnh giới (miễn phí), không ghi một lựa chọn thừa.
        int tier = requested == unlocked.getNumber() ? 0 : requested;
        CostumePort.Outcome outcome = costumes.choose(characterId, tier, clock.instant());
        if (outcome != CostumePort.Outcome.OK) {
            return response(SetCostumeOutcome.SET_COSTUME_OUTCOME_NOT_ENOUGH_GOLD, characterId, unlocked, 0L);
        }
        appearanceRefresh.onLevelReached(characterId, level);
        return response(
                SetCostumeOutcome.SET_COSTUME_OUTCOME_OK,
                characterId,
                unlocked,
                tier == 0 ? 0L : CostumeFacade.CHANGE_FEE);
    }

    private SetCostumeResponse response(SetCostumeOutcome outcome, long characterId, CostumeTier unlocked, long fee) {
        int chosen = costumes.chosenTier(characterId);
        CostumeTier shown = chosen > 0 && chosen < unlocked.getNumber() ? CostumeTier.forNumber(chosen) : unlocked;
        return SetCostumeResponse.newBuilder()
                .setOutcome(outcome)
                .setShownTier(shown)
                .setFee(fee)
                .build();
    }
}
