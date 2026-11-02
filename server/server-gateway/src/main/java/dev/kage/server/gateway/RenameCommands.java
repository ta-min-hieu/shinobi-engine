package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Instant;

import dev.kage.character.api.RenameFacade;
import dev.kage.character.domain.RenamePort;
import dev.kage.protocol.RenameCharacterCommand;
import dev.kage.protocol.RenameCharacterOutcome;
import dev.kage.protocol.RenameCharacterResponse;

/**
 * Dịch Danh Phù (ADR-148) — đổi tên nhân vật đang chơi. Mọi chỗ hiển thị tên (chat, bạn bè, bang, thư, bảng xếp hạng)
 * đều tra tên theo id lúc cần, nên đổi xong là có hiệu lực ngay, không phải dọn cache nào trong RAM.
 */
final class RenameCommands {

    private final RenameFacade facade;
    private final Clock clock;

    RenameCommands(RenameFacade facade, Clock clock) {
        this.facade = facade;
        this.clock = clock;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    RenameCharacterResponse rename(long characterId, RenameCharacterCommand command) {
        RenamePort.Result result = facade.rename(characterId, command.getNewName(), clock.instant());
        return RenameCharacterResponse.newBuilder()
                .setOutcome(wire(result.outcome()))
                .setName(result.name())
                .setNextAllowedAtEpochMs(
                        result.nextAllowedAt().map(Instant::toEpochMilli).orElse(0L))
                .build();
    }

    private static RenameCharacterOutcome wire(RenamePort.Outcome outcome) {
        return switch (outcome) {
            case OK -> RenameCharacterOutcome.RENAME_CHARACTER_OUTCOME_OK;
            case INVALID_NAME -> RenameCharacterOutcome.RENAME_CHARACTER_OUTCOME_INVALID_NAME;
            case SAME_NAME -> RenameCharacterOutcome.RENAME_CHARACTER_OUTCOME_SAME_NAME;
            case NAME_TAKEN -> RenameCharacterOutcome.RENAME_CHARACTER_OUTCOME_NAME_TAKEN;
            case NO_TALISMAN -> RenameCharacterOutcome.RENAME_CHARACTER_OUTCOME_NO_TALISMAN;
            case ON_COOLDOWN -> RenameCharacterOutcome.RENAME_CHARACTER_OUTCOME_ON_COOLDOWN;
            case NOT_FOUND -> RenameCharacterOutcome.RENAME_CHARACTER_OUTCOME_NOT_FOUND;
        };
    }
}
