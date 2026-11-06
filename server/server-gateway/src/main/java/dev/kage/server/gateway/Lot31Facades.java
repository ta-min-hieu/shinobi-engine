package dev.kage.server.gateway;

import dev.kage.guild.api.GuildNoticeFacade;
import dev.kage.inventory.api.EquipmentPresetFacade;
import dev.kage.market.api.MarketHistoryFacade;

/** Facade lô 31 (ADR-175…) gom thành MỘT thành phần của {@link ExpansionFacades}. */
record Lot31Facades(
        EquipmentPresetFacade equipmentPresets, MarketHistoryFacade marketHistory, GuildNoticeFacade guildNotice) {

    /** Composition không có các tính năng này (test). */
    static final Lot31Facades DISABLED =
            new Lot31Facades(EquipmentPresetFacade.DISABLED, MarketHistoryFacade.DISABLED, GuildNoticeFacade.DISABLED);
}
