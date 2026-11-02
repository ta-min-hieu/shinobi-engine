package dev.kage.server.gateway;

import java.time.Clock;

import dev.kage.character.api.MilestoneFacade;
import dev.kage.character.domain.MilestonePort;
import dev.kage.protocol.ClaimRealmMilestoneCommand;
import dev.kage.protocol.ClaimRealmMilestoneOutcome;
import dev.kage.protocol.ClaimRealmMilestoneResponse;
import dev.kage.protocol.GetRealmMilestonesResponse;
import dev.kage.protocol.RealmMilestoneView;

/** Quà mốc cảnh giới (ADR-153) — xem bảng mốc, nhận quà; server kiểm cấp và chống nhận hai lần. */
final class MilestoneCommands {

    private final MilestoneFacade facade;
    private final Clock clock;

    MilestoneCommands(MilestoneFacade facade, Clock clock) {
        this.facade = facade;
        this.clock = clock;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    GetRealmMilestonesResponse board(long characterId) {
        MilestonePort.Board board = facade.board(characterId);
        GetRealmMilestonesResponse.Builder response =
                GetRealmMilestonesResponse.newBuilder().setCharacterLevel(board.characterLevel());
        for (MilestonePort.Milestone m : board.milestones()) {
            response.addMilestones(RealmMilestoneView.newBuilder()
                    .setLevel(m.level())
                    .setNameKey(m.nameKey())
                    .setGold(m.gold())
                    .setItemTemplateId(m.itemTemplateId())
                    .setQuantity(m.quantity())
                    .setClaimed(m.claimed()));
        }
        return response.build();
    }

    /** Virtual Thread; {@code characterId} từ session, cấp mốc từ gói nhưng server kiểm lại cấp nhân vật. */
    ClaimRealmMilestoneResponse claim(long characterId, ClaimRealmMilestoneCommand command) {
        MilestonePort.Outcome outcome = facade.claim(characterId, command.getLevel(), clock.instant());
        return ClaimRealmMilestoneResponse.newBuilder()
                .setOutcome(wire(outcome))
                .setLevel(command.getLevel())
                .setBoard(board(characterId))
                .build();
    }

    private static ClaimRealmMilestoneOutcome wire(MilestonePort.Outcome outcome) {
        return switch (outcome) {
            case OK -> ClaimRealmMilestoneOutcome.CLAIM_REALM_MILESTONE_OUTCOME_OK;
            case UNKNOWN_MILESTONE -> ClaimRealmMilestoneOutcome.CLAIM_REALM_MILESTONE_OUTCOME_UNKNOWN_MILESTONE;
            case LEVEL_TOO_LOW -> ClaimRealmMilestoneOutcome.CLAIM_REALM_MILESTONE_OUTCOME_LEVEL_TOO_LOW;
            case ALREADY_CLAIMED -> ClaimRealmMilestoneOutcome.CLAIM_REALM_MILESTONE_OUTCOME_ALREADY_CLAIMED;
            case BAG_FULL -> ClaimRealmMilestoneOutcome.CLAIM_REALM_MILESTONE_OUTCOME_BAG_FULL;
            case GOLD_CAP -> ClaimRealmMilestoneOutcome.CLAIM_REALM_MILESTONE_OUTCOME_GOLD_CAP;
        };
    }
}
