package dev.kage.server.gateway;

import dev.kage.guild.domain.GuildItemDonationPort;
import dev.kage.protocol.DonateItemToGuildCommand;
import dev.kage.protocol.DonateItemToGuildOutcome;
import dev.kage.protocol.DonateItemToGuildResponse;

/** Lớp lệnh lô 35 (ADR-207…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot35Commands(Lot35Facades facades, ConnectedCharacterRegistry registry) {

    static Lot35Commands of(GatewayFacades gateway, WorldGateway worldGateway) {
        return new Lot35Commands(gateway.progression().expansion().lot34().lot35(), worldGateway.registry());
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
