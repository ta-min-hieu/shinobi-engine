package dev.kage.server.gateway;

import dev.kage.character.api.GoldHistoryFacade;
import dev.kage.character.api.InspectPrivacyFacade;
import dev.kage.guild.api.GuildRenameFacade;
import dev.kage.inventory.api.GemRemovalFacade;
import dev.kage.inventory.api.JunkSaleFacade;

/** Facade lô 30 (ADR-167…) gom thành MỘT thành phần của {@link ExpansionFacades}. */
record Lot30Facades(
        GemRemovalFacade gemRemoval,
        GoldHistoryFacade goldHistory,
        JunkSaleFacade junkSale,
        InspectPrivacyFacade inspectPrivacy,
        GuildRenameFacade guildRename) {

    /** Composition không có các tính năng này (test). */
    static final Lot30Facades DISABLED = new Lot30Facades(
            GemRemovalFacade.DISABLED,
            GoldHistoryFacade.DISABLED,
            JunkSaleFacade.DISABLED,
            InspectPrivacyFacade.DISABLED,
            GuildRenameFacade.DISABLED);
}
