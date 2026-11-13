package dev.kage.server.gateway;

import dev.kage.character.api.ProfileLikeFacade;
import dev.kage.mail.api.ScheduledMailFacade;
import dev.kage.monster.api.BossKillLogFacade;

/** Facade lô 46 (ADR-295…) gom thành MỘT thành phần của {@link Lot45Facades}. */
record Lot46Facades(
        BossKillLogFacade bossKills,
        ScheduledMailFacade scheduledMail,
        ProfileLikeFacade profileLikes,
        Lot48Facades lot48) {

    /** Composition không có các tính năng này (test). */
    static final Lot46Facades DISABLED = new Lot46Facades(
            BossKillLogFacade.DISABLED,
            ScheduledMailFacade.DISABLED,
            ProfileLikeFacade.DISABLED,
            Lot48Facades.DISABLED);
}
