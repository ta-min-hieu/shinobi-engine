package dev.kage.server.gateway;

import dev.kage.character.api.LifetimeRankingFacade;
import dev.kage.guild.api.GuildDirectoryFacade;

/** Facade lô 58 (ADR-391…) gom thành MỘT thành phần của {@link Lot57Facades}. Quà quay lại dùng facade thư. */
record Lot58Facades(LifetimeRankingFacade lifetimeRanking, GuildDirectoryFacade guildDirectory) {

    /** Composition không có các tính năng này (test). */
    static final Lot58Facades DISABLED =
            new Lot58Facades(LifetimeRankingFacade.DISABLED, GuildDirectoryFacade.DISABLED);
}
