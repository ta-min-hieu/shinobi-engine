package dev.kage.server.gateway;

import dev.kage.friend.api.LoginGreetingFacade;
import dev.kage.guild.api.GuildNameHistoryFacade;
import dev.kage.pvp.api.EnemyAlertFacade;

/** Facade lô 62 (ADR-423…) gom thành MỘT thành phần của {@link Lot61Facades}. Mời bạn vào nhóm dùng facade nhóm. */
record Lot62Facades(GuildNameHistoryFacade guildNames, LoginGreetingFacade greetings, EnemyAlertFacade enemyAlerts) {

    /** Composition không có các tính năng này (test). */
    static final Lot62Facades DISABLED =
            new Lot62Facades(GuildNameHistoryFacade.DISABLED, LoginGreetingFacade.DISABLED, EnemyAlertFacade.DISABLED);
}
