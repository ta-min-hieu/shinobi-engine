package dev.kage.server.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.party.api.PartyFacade;
import dev.kage.party.domain.PartyFinderBoard;
import dev.kage.party.domain.PartyFinderListing;
import dev.kage.party.domain.PartyGoal;
import dev.kage.protocol.JoinPartyFinderCommand;
import dev.kage.protocol.JoinPartyFinderOutcome;
import dev.kage.protocol.JoinPartyFinderResponse;
import dev.kage.protocol.ListPartyFinderResponse;
import dev.kage.protocol.PartyFinderListingView;
import dev.kage.protocol.PostPartyFinderCommand;
import dev.kage.protocol.PostPartyFinderOutcome;
import dev.kage.protocol.PostPartyFinderResponse;
import dev.kage.protocol.RemovePartyFinderResponse;

/**
 * Bảng tìm nhóm (ADR-082) — tách khỏi {@code GatewayChannelHandler}. Tin chỉ trong RAM ({@link PartyFinderBoard}); vào
 * nhóm đi đúng hai bước có sẵn của {@link PartyFacade} (mời thay trưởng nhóm → chấp nhận), nên mọi luật nhóm (trần 5
 * người, một người một nhóm, khoá chống race) vẫn do feature-party giữ. Chạy trên Virtual Thread (chạm DB).
 */
final class PartyFinderCommands {

    private final PartyFacade parties;
    private final CharacterFacade characters;
    private final PartyFinderBoard board;

    PartyFinderCommands(PartyFacade parties, CharacterFacade characters, PartyFinderBoard board) {
        this.parties = parties;
        this.characters = characters;
        this.board = board;
    }

    ListPartyFinderResponse list() {
        ListPartyFinderResponse.Builder response = ListPartyFinderResponse.newBuilder();
        Instant now = Instant.now();
        for (PartyFinderListing listing : board.active()) {
            response.addListings(PartyFinderListingView.newBuilder()
                    .setLeaderCharacterId(listing.leaderId())
                    .setLeaderName(listing.leaderName())
                    .setGoal(toProto(listing.goal()))
                    .setMinLevel(listing.minLevel())
                    .setExpiresInSeconds(Math.max(
                            0L, Duration.between(now, listing.expiresAt()).toSeconds())));
        }
        return response.build();
    }

    PostPartyFinderResponse post(long characterId, PostPartyFinderCommand command) {
        Optional<PartyGoal> goal = fromProto(command.getGoal());
        if (goal.isEmpty()) {
            return postResponse(PostPartyFinderOutcome.POST_PARTY_FINDER_OUTCOME_INVALID_GOAL);
        }
        Optional<PartyFacade.MyPartyView> party = parties.findMyParty(characterId);
        if (party.isPresent() && !party.get().isLeader()) {
            return postResponse(PostPartyFinderOutcome.POST_PARTY_FINDER_OUTCOME_NOT_LEADER);
        }
        if (party.isEmpty()) {
            parties.createParty(characterId); // D3 — đăng tin khi chưa có nhóm thì lập nhóm trước
        }
        String name = characters.findById(characterId).map(Character::name).orElse("#" + characterId);
        board.post(characterId, name, goal.get(), command.getMinLevel());
        return postResponse(PostPartyFinderOutcome.POST_PARTY_FINDER_OUTCOME_OK);
    }

    JoinPartyFinderResponse join(long characterId, JoinPartyFinderCommand command) {
        return JoinPartyFinderResponse.newBuilder()
                .setOutcome(joinOutcome(characterId, command.getLeaderCharacterId()))
                .setBoard(list())
                .build();
    }

    private JoinPartyFinderOutcome joinOutcome(long characterId, long leaderId) {
        if (leaderId == characterId) {
            return JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_OWN_LISTING;
        }
        Optional<PartyFinderListing> listing = board.find(leaderId);
        if (listing.isEmpty()) {
            return JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_NOT_FOUND;
        }
        int level = characters.findById(characterId).map(Character::level).orElse(1);
        if (level < listing.get().minLevel()) {
            return JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_LEVEL_TOO_LOW;
        }
        return invite(characterId, leaderId);
    }

    /** Bước một: mời thay trưởng nhóm — tin công khai là lời mời công khai (D4). */
    private JoinPartyFinderOutcome invite(long characterId, long leaderId) {
        PartyFacade.InviteResult invited = parties.invite(leaderId, characterId);
        return switch (invited) {
            case OK -> accept(characterId, leaderId);
            case NOT_LEADER -> dropListing(leaderId, JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_NOT_FOUND);
            case PARTY_FULL -> dropListing(leaderId, JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_PARTY_FULL);
            case TARGET_ALREADY_IN_PARTY -> JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_ALREADY_IN_PARTY;
            case INVITE_ALREADY_PENDING -> JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_INVITE_PENDING;
            case CANNOT_INVITE_SELF -> JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_OWN_LISTING;
        };
    }

    /** Bước hai: chấp nhận đúng lời mời vừa tạo (của nhóm người đăng tin). */
    private JoinPartyFinderOutcome accept(long characterId, long leaderId) {
        Optional<Long> partyId = parties.findMyParty(leaderId).map(PartyFacade.MyPartyView::partyId);
        Optional<PartyFacade.PendingInviteView> invite = parties.listPendingInvites(characterId).stream()
                .filter(pending -> partyId.isPresent() && pending.partyId() == partyId.get())
                .findFirst();
        if (invite.isEmpty()) {
            return JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_NOT_FOUND;
        }
        return switch (parties.acceptInvite(invite.get().inviteId(), characterId)) {
            case OK -> JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_OK;
            case ALREADY_IN_PARTY -> JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_ALREADY_IN_PARTY;
            case PARTY_FULL -> dropListing(leaderId, JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_PARTY_FULL);
            case NOT_FOUND -> JoinPartyFinderOutcome.JOIN_PARTY_FINDER_OUTCOME_NOT_FOUND;
        };
    }

    /** Nhóm đầy hoặc không còn trưởng nhóm đó — tin tự biến mất (D4). */
    private JoinPartyFinderOutcome dropListing(long leaderId, JoinPartyFinderOutcome outcome) {
        board.remove(leaderId);
        return outcome;
    }

    RemovePartyFinderResponse remove(long characterId) {
        boolean removed = board.remove(characterId);
        return RemovePartyFinderResponse.newBuilder()
                .setRemoved(removed)
                .setBoard(list())
                .build();
    }

    private PostPartyFinderResponse postResponse(PostPartyFinderOutcome outcome) {
        return PostPartyFinderResponse.newBuilder()
                .setOutcome(outcome)
                .setBoard(list())
                .build();
    }

    private static dev.kage.protocol.PartyGoal toProto(PartyGoal goal) {
        return switch (goal) {
            case LEVELING -> dev.kage.protocol.PartyGoal.PARTY_GOAL_LEVELING;
            case QUEST -> dev.kage.protocol.PartyGoal.PARTY_GOAL_QUEST;
            case DUNGEON -> dev.kage.protocol.PartyGoal.PARTY_GOAL_DUNGEON;
            case WORLD_BOSS -> dev.kage.protocol.PartyGoal.PARTY_GOAL_WORLD_BOSS;
        };
    }

    private static Optional<PartyGoal> fromProto(dev.kage.protocol.PartyGoal goal) {
        return switch (goal) {
            case PARTY_GOAL_LEVELING -> Optional.of(PartyGoal.LEVELING);
            case PARTY_GOAL_QUEST -> Optional.of(PartyGoal.QUEST);
            case PARTY_GOAL_DUNGEON -> Optional.of(PartyGoal.DUNGEON);
            case PARTY_GOAL_WORLD_BOSS -> Optional.of(PartyGoal.WORLD_BOSS);
            case PARTY_GOAL_UNSPECIFIED, UNRECOGNIZED -> Optional.empty();
        };
    }
}
