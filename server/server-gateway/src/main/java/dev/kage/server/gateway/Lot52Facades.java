package dev.kage.server.gateway;

import dev.kage.character.api.NameHistoryFacade;
import dev.kage.guild.api.GuildTagFacade;

/** Facade lô 52 (ADR-343…) gom thành MỘT thành phần của {@link Lot51Facades}. */
record Lot52Facades(NameHistoryFacade nameHistory, GuildTagFacade guildTags) {

    /** Composition không có các tính năng này (test). */
    static final Lot52Facades DISABLED = new Lot52Facades(NameHistoryFacade.DISABLED, GuildTagFacade.DISABLED);
}
