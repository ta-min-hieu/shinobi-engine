package dev.kage.server.gateway;

import dev.kage.guild.domain.GuildItemDonationPort;
import dev.kage.protocol.DonateItemToGuildCommand;
import dev.kage.protocol.DonateItemToGuildOutcome;
import dev.kage.protocol.DonateItemToGuildResponse;

/** Lớp lệnh lô 35 (ADR-207…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot35Commands(Lot35Facades facades, ConnectedCharacterRegistry registry, Social social) {

    /** Nguồn xã hội cho gợi ý kết bạn (ADR-211). */
    record Social(
            dev.kage.character.api.CharacterFacade characters,
            dev.kage.friend.api.FriendFacade friends,
            dev.kage.friend.api.BlockFacade blocks,
            dev.kage.party.api.PartyFacade party) {}

    static Lot35Commands of(GatewayFacades gateway, WorldGateway worldGateway) {
        return new Lot35Commands(
                gateway.progression().expansion().lot34().lot35(),
                worldGateway.registry(),
                new Social(
                        gateway.characterFacade(),
                        gateway.contentFacades().socialFacades().friendFacade(),
                        gateway.progression().relationships().blocks(),
                        gateway.contentFacades().socialFacades().partyFacade()));
    }

    /** ADR-211 — Virtual Thread; tối đa 10 người online nên kết bạn với {@code characterId}. */
    dev.kage.protocol.SuggestFriendsResponse suggestFriends(long characterId) {
        int myLevel = social.characters()
                .findById(characterId)
                .map(dev.kage.character.domain.Character::level)
                .orElse(1);
        java.util.Set<Long> excluded = new java.util.HashSet<>(social.friends().listFriends(characterId));
        excluded.add(characterId);
        social.friends().listPendingRequests(characterId).forEach(p -> excluded.add(p.requesterId()));
        dev.kage.protocol.SuggestFriendsResponse.Builder response =
                dev.kage.protocol.SuggestFriendsResponse.newBuilder();
        for (FriendSuggestions.Candidate c : FriendSuggestions.pick(
                myLevel,
                new FriendSuggestions.Sources(
                        registry.onlineCharacterIds(),
                        excluded,
                        other -> social.blocks().eitherBlocks(characterId, other),
                        id -> social.characters()
                                .findById(id)
                                .map(ch -> new FriendSuggestions.Candidate(ch.id(), ch.name(), ch.level()))))) {
            response.addSuggestions(dev.kage.protocol.FriendSuggestion.newBuilder()
                    .setName(c.name())
                    .setLevel(c.level()));
        }
        return response.build();
    }

    /** ADR-213 — Virtual Thread; trưởng nhóm đánh dấu (0 = bỏ) một mục tiêu, báo cả nhóm đang online. */
    dev.kage.protocol.MarkPartyTargetResponse markPartyTarget(
            long characterId, dev.kage.protocol.MarkPartyTargetCommand command) {
        dev.kage.protocol.MarkPartyTargetResponse.Builder response =
                dev.kage.protocol.MarkPartyTargetResponse.newBuilder();
        if (command.getTargetEntityId() < 0) {
            return response.setOutcome(
                            dev.kage.protocol.MarkPartyTargetOutcome.MARK_PARTY_TARGET_OUTCOME_INVALID_TARGET)
                    .build();
        }
        if (!social.party()
                .findMyParty(characterId)
                .map(dev.kage.party.api.PartyFacade.MyPartyView::isLeader)
                .orElse(false)) {
            return response.setOutcome(dev.kage.protocol.MarkPartyTargetOutcome.MARK_PARTY_TARGET_OUTCOME_NOT_LEADER)
                    .build();
        }
        byte[] event = dev.kage.protocol.PartyTargetMarkedEvent.newBuilder()
                .setTargetEntityId(command.getTargetEntityId())
                .setLeaderName(social.characters()
                        .findById(characterId)
                        .map(dev.kage.character.domain.Character::name)
                        .orElse(""))
                .build()
                .toByteArray();
        for (dev.kage.party.api.PartyFacade.MemberView member : social.party().listMembers(characterId)) {
            registry.sendToCharacterIfOnline(
                    member.characterId(), dev.kage.protocol.Opcode.OPCODE_PARTY_TARGET_MARKED_EVENT_VALUE, 0, event);
        }
        return response.setOutcome(dev.kage.protocol.MarkPartyTargetOutcome.MARK_PARTY_TARGET_OUTCOME_OK)
                .build();
    }

    /** ADR-212 — Virtual Thread; bật / tắt (hoặc chỉ hỏi) chặn thư người lạ. */
    dev.kage.protocol.SetStrangerMailBlockResponse setStrangerMailBlock(
            long characterId, dev.kage.protocol.SetStrangerMailBlockCommand command) {
        if (!command.getQueryOnly()) {
            facades.strangerMail().setBlocksStrangers(characterId, command.getEnabled());
        }
        return dev.kage.protocol.SetStrangerMailBlockResponse.newBuilder()
                .setEnabled(facades.strangerMail().blocksStrangers(characterId))
                .build();
    }

    /** ADR-207 — Virtual Thread; góp vật phẩm cho bang. Bang lên cấp thì báo cả bang như góp linh thạch. */
    DonateItemToGuildResponse donateItem(long characterId, DonateItemToGuildCommand command) {
        GuildItemDonationPort.Donation donation =
                facades.guildItemDonation().donate(characterId, command.getInventoryItemId(), command.getQuantity());
        if (donation.leveledUp()) {
            registry.guildProgress.announceLevelUp(donation.guildId());
        }
        return DonateItemToGuildResponse.newBuilder()
                .setOutcome(DonateItemToGuildOutcome.valueOf(
                        "DONATE_ITEM_TO_GUILD_OUTCOME_" + donation.outcome().name()))
                .setContribution(donation.contribution())
                .setGuildLeveledUp(donation.leveledUp())
                .build();
    }

    /** ADR-208 — Virtual Thread; các trận bang chiến đã chốt gần nhất của bang {@code characterId}. */
    dev.kage.protocol.GetGuildWarHistoryResponse warHistory(long characterId) {
        dev.kage.protocol.GetGuildWarHistoryResponse.Builder response =
                dev.kage.protocol.GetGuildWarHistoryResponse.newBuilder();
        for (dev.kage.guild.domain.GuildWarHistoryPort.PastWar war :
                facades.warHistory().recentOf(characterId)) {
            response.addEntries(dev.kage.protocol.GuildWarHistoryEntry.newBuilder()
                    .setOpponentGuildName(war.opponentName())
                    .setDeclaredByUs(war.declaredByUs())
                    .setOurKills(war.ourKills())
                    .setTheirKills(war.theirKills())
                    .setResult(dev.kage.protocol.GuildWarResult.valueOf(
                            "GUILD_WAR_RESULT_" + war.result().name()))
                    .setEndedEpochSeconds(war.endedAt().getEpochSecond()));
        }
        return response.build();
    }
}
