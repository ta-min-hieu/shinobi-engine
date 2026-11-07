package dev.kage.server.gateway;

import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.chat.domain.ProfanityFilter;
import dev.kage.inventory.api.PetNameFacade;
import dev.kage.party.api.PartyFacade;
import dev.kage.party.application.HandOverPartyLeadershipUseCase;
import dev.kage.protocol.EnemyView;
import dev.kage.protocol.ListEnemiesResponse;
import dev.kage.protocol.RemoveEnemyCommand;
import dev.kage.protocol.RemoveEnemyResponse;
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

    /** ADR-194 — Virtual Thread; cừu nhân của {@code characterId}, kèm ai đang online. */
    ListEnemiesResponse listEnemies(long characterId) {
        ListEnemiesResponse.Builder response = ListEnemiesResponse.newBuilder();
        for (dev.kage.pvp.domain.EnemyListPort.Enemy enemy : facades.enemies().list(characterId)) {
            response.addEnemies(EnemyView.newBuilder()
                    .setName(enemy.name())
                    .setKillCount(enemy.killCount())
                    .setLastKilledEpochSeconds(enemy.lastKilledAt().getEpochSecond())
                    .setOnline(registry.isOnline(enemy.characterId())));
        }
        return response.build();
    }

    /** ADR-194 — Virtual Thread; xoá một cừu nhân theo tên. */
    RemoveEnemyResponse removeEnemy(long characterId, RemoveEnemyCommand command) {
        boolean removed = facades.enemies().remove(characterId, command.getName());
        return RemoveEnemyResponse.newBuilder()
                .setRemoved(removed)
                .setName(command.getName().strip())
                .build();
    }

    /** ADR-196 — Virtual Thread; đặt / sửa / bỏ ({@code max_unit_price = 0}) một dòng theo dõi phường thị. */
    dev.kage.protocol.SetMarketWatchResponse setMarketWatch(
            long characterId, dev.kage.protocol.SetMarketWatchCommand command) {
        dev.kage.market.api.MarketWatchFacade.Outcome outcome = facades.marketWatch()
                .set(characterId, command.getItemTemplateId(), command.getMaxUnitPrice(), java.time.Instant.now());
        return dev.kage.protocol.SetMarketWatchResponse.newBuilder()
                .setOutcome(
                        dev.kage.protocol.SetMarketWatchOutcome.valueOf("SET_MARKET_WATCH_OUTCOME_" + outcome.name()))
                .setItemTemplateId(command.getItemTemplateId())
                .setMaxUnitPrice(command.getMaxUnitPrice())
                .build();
    }

    /** ADR-196 — Virtual Thread; các dòng theo dõi phường thị của {@code characterId}. */
    dev.kage.protocol.ListMarketWatchResponse listMarketWatch(long characterId) {
        dev.kage.protocol.ListMarketWatchResponse.Builder response =
                dev.kage.protocol.ListMarketWatchResponse.newBuilder()
                        .setMaxWatches(dev.kage.market.api.MarketWatchFacade.MAX_WATCHES);
        for (dev.kage.market.domain.MarketWatchPort.Watch watch :
                facades.marketWatch().list(characterId)) {
            response.addWatches(dev.kage.protocol.MarketWatchView.newBuilder()
                    .setItemTemplateId(watch.itemTemplateId())
                    .setMaxUnitPrice(watch.maxUnitPrice()));
        }
        return response.build();
    }
}
