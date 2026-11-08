package dev.kage.server.gateway;

import dev.kage.guild.api.GuildPollFacade;

/** Facade lô 40 (ADR-247…) gom thành MỘT thành phần của {@link Lot39Facades}. */
record Lot40Facades(GuildPollFacade guildPoll) {

    /** Composition không có các tính năng này (test). */
    static final Lot40Facades DISABLED = new Lot40Facades(GuildPollFacade.DISABLED);
}
