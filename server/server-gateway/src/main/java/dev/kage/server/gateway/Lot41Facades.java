package dev.kage.server.gateway;

import dev.kage.guild.api.GuildEventFacade;
import dev.kage.monster.api.MonsterInfoFacade;

/** Facade lô 41 (ADR-255…) gom thành MỘT thành phần của {@link Lot40Facades}. */
record Lot41Facades(MonsterInfoFacade monsterInfo, GuildEventFacade guildEvents, Lot42Facades lot42) {

    /** Composition không có các tính năng này (test). */
    static final Lot41Facades DISABLED =
            new Lot41Facades(MonsterInfoFacade.DISABLED, GuildEventFacade.DISABLED, Lot42Facades.DISABLED);
}
