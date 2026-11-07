package dev.kage.server.gateway;

import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.chat.domain.ProfanityFilter;
import dev.kage.inventory.api.PetNameFacade;
import dev.kage.party.api.PartyFacade;
import dev.kage.party.application.HandOverPartyLeadershipUseCase;
import dev.kage.protocol.RenamePetCommand;
import dev.kage.protocol.RenamePetOutcome;
import dev.kage.protocol.RenamePetResponse;
import dev.kage.protocol.TransferPartyLeaderCommand;
import dev.kage.protocol.TransferPartyLeaderOutcome;
import dev.kage.protocol.TransferPartyLeaderResponse;

/** Lớp lệnh lô 33 (ADR-191…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot33Commands(
        PartyFacade parties, CharacterFacade characters, ConnectedCharacterRegistry registry, Lot33Facades facades) {

    static Lot33Commands of(GatewayFacades gateway, WorldGateway worldGateway) {
        return new Lot33Commands(
                gateway.contentFacades().socialFacades().partyFacade(),
                gateway.characterFacade(),
                worldGateway.registry(),
                gateway.progression().expansion().lot33());
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

    /** ADR-193 — Virtual Thread; đặt (hoặc xoá, khi rỗng) tên linh thú đang mặc của {@code characterId}. */
    RenamePetResponse renamePet(long characterId, RenamePetCommand command) {
        String name = command.getName().strip();
        RenamePetResponse.Builder response = RenamePetResponse.newBuilder().setName(name);
        if (ProfanityFilter.containsBannedWord(name)) {
            return response.setOutcome(RenamePetOutcome.RENAME_PET_OUTCOME_BANNED_WORD)
                    .build();
        }
        PetNameFacade.Outcome outcome = facades.petName().rename(characterId, name);
        return response.setOutcome(RenamePetOutcome.valueOf("RENAME_PET_OUTCOME_" + outcome.name()))
                .build();
    }
}
