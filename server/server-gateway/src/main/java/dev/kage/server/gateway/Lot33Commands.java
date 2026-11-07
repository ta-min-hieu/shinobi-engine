package dev.kage.server.gateway;

import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.party.api.PartyFacade;
import dev.kage.party.application.HandOverPartyLeadershipUseCase;
import dev.kage.protocol.TransferPartyLeaderCommand;
import dev.kage.protocol.TransferPartyLeaderOutcome;
import dev.kage.protocol.TransferPartyLeaderResponse;

/** Lớp lệnh lô 33 (ADR-191…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot33Commands(PartyFacade parties, CharacterFacade characters, ConnectedCharacterRegistry registry) {

    static Lot33Commands of(GatewayFacades gateway, WorldGateway worldGateway) {
        return new Lot33Commands(
                gateway.contentFacades().socialFacades().partyFacade(),
                gateway.characterFacade(),
                worldGateway.registry());
    }

    /** ADR-191 — Virtual Thread; {@code characterId} (trưởng nhóm hiện tại) từ session. Xong thì báo cả nhóm. */
    TransferPartyLeaderResponse transferPartyLeader(long characterId, TransferPartyLeaderCommand command) {
        Optional<Character> target =
                characters.findByName(command.getTargetName().strip());
        TransferPartyLeaderResponse.Builder response = TransferPartyLeaderResponse.newBuilder();
        if (target.isEmpty()) {
            return response.setOutcome(TransferPartyLeaderOutcome.TRANSFER_PARTY_LEADER_OUTCOME_TARGET_NOT_IN_PARTY)
                    .build();
        }
        HandOverPartyLeadershipUseCase.Transfer result =
                parties.transferLeadership(characterId, target.get().id());
        if (result == HandOverPartyLeadershipUseCase.Transfer.OK) {
            java.util.List<Long> members = parties.listMembers(characterId).stream()
                    .map(PartyFacade.MemberView::characterId)
                    .toList();
            new MembershipNotices(registry).partyLeaderChanged(target.get().id(), members);
        }
        return response.setOutcome(TransferPartyLeaderOutcome.valueOf("TRANSFER_PARTY_LEADER_OUTCOME_" + result.name()))
                .setTargetName(target.get().name())
                .build();
    }
}
