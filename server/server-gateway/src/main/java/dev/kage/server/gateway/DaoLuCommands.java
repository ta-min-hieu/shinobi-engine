package dev.kage.server.gateway;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.friend.api.DaoLuFacade;
import dev.kage.friend.domain.DaoLuOutcome;
import dev.kage.protocol.AcceptDaoLuResponse;
import dev.kage.protocol.DaoLuProposalEvent;
import dev.kage.protocol.DissolveDaoLuResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.ProposeDaoLuResponse;

/** Đạo lữ (ADR-094) — tra tên, gọi feature-friend, báo người kia. Chạy trên Virtual Thread (chạm DB). */
final class DaoLuCommands {

    private static final Map<DaoLuOutcome, dev.kage.protocol.DaoLuOutcome> OUTCOMES = new EnumMap<>(DaoLuOutcome.class);

    static {
        for (DaoLuOutcome outcome : DaoLuOutcome.values()) {
            OUTCOMES.put(outcome, dev.kage.protocol.DaoLuOutcome.valueOf("DAO_LU_OUTCOME_" + outcome.name()));
        }
    }

    private final DaoLuFacade daoLu;
    private final CharacterFacade characters;
    private final BlockCommands blocks;
    private final PlayerNotices notices;

    DaoLuCommands(DaoLuFacade daoLu, CharacterFacade characters, BlockCommands blocks, PlayerNotices notices) {
        this.daoLu = daoLu;
        this.characters = characters;
        this.blocks = blocks;
        this.notices = notices;
    }

    ProposeDaoLuResponse propose(long proposerId, String targetName) {
        Optional<Character> target = characters.findByName(targetName.strip());
        if (target.isEmpty()) {
            return proposeResponse(dev.kage.protocol.DaoLuOutcome.DAO_LU_OUTCOME_TARGET_NOT_FOUND);
        }
        blocks.requireNotBlocked(proposerId, target.get().id());
        if (!notices.isOnline(target.get().id())) {
            return proposeResponse(dev.kage.protocol.DaoLuOutcome.DAO_LU_OUTCOME_TARGET_OFFLINE);
        }
        DaoLuOutcome outcome = daoLu.propose(proposerId, target.get().id());
        if (outcome == DaoLuOutcome.OK) {
            String proposerName =
                    characters.findById(proposerId).map(Character::name).orElse("#" + proposerId);
            notices.event(
                    target.get().id(),
                    Opcode.OPCODE_DAO_LU_PROPOSAL_EVENT_VALUE,
                    DaoLuProposalEvent.newBuilder()
                            .setProposerId(proposerId)
                            .setProposerName(proposerName)
                            .build()
                            .toByteArray());
        }
        return proposeResponse(OUTCOMES.get(outcome));
    }

    AcceptDaoLuResponse accept(long accepterId, String proposerName) {
        Optional<Character> proposer = characters.findByName(proposerName.strip());
        if (proposer.isEmpty()) {
            return AcceptDaoLuResponse.newBuilder()
                    .setOutcome(dev.kage.protocol.DaoLuOutcome.DAO_LU_OUTCOME_TARGET_NOT_FOUND)
                    .build();
        }
        blocks.requireNotBlocked(accepterId, proposer.get().id());
        DaoLuOutcome outcome = daoLu.accept(accepterId, proposer.get().id());
        if (outcome == DaoLuOutcome.OK) {
            String accepterName =
                    characters.findById(accepterId).map(Character::name).orElse("#" + accepterId);
            notices.systemLine(
                    proposer.get().id(),
                    "%s đã nhận lời kết đạo lữ. / %s accepted your proposal.".formatted(accepterName, accepterName));
        }
        return AcceptDaoLuResponse.newBuilder()
                .setOutcome(OUTCOMES.get(outcome))
                .build();
    }

    DissolveDaoLuResponse dissolve(long characterId) {
        Optional<Long> partner = daoLu.dissolve(characterId);
        partner.ifPresent(id -> {
            String name = characters.findById(characterId).map(Character::name).orElse("#" + characterId);
            notices.systemLine(id, "%s đã giải đạo lữ. / %s dissolved your bond.".formatted(name, name));
        });
        return DissolveDaoLuResponse.newBuilder()
                .setOutcome(OUTCOMES.get(partner.isPresent() ? DaoLuOutcome.OK : DaoLuOutcome.NOT_BOUND))
                .build();
    }

    /** Đạo lữ hiện tại — cho cờ {@code FriendView.dao_lu}. */
    Optional<Long> partnerOf(long characterId) {
        return daoLu.partnerOf(characterId);
    }

    private static ProposeDaoLuResponse proposeResponse(dev.kage.protocol.DaoLuOutcome outcome) {
        return ProposeDaoLuResponse.newBuilder().setOutcome(outcome).build();
    }
}
