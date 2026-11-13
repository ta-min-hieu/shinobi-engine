package dev.kage.server.gateway;

import dev.kage.character.api.DeathLogFacade;
import dev.kage.chat.api.BulletinFacade;
import dev.kage.friend.api.StatusMessageFacade;

/** Facade lô 51 (ADR-335…) gom thành MỘT thành phần của {@link Lot50Facades}. Biểu quyết đuổi (ADR-338) ở RAM. */
record Lot51Facades(StatusMessageFacade statusMessages, BulletinFacade bulletin, DeathLogFacade deathLog) {

    /** Composition không có các tính năng này (test). */
    static final Lot51Facades DISABLED =
            new Lot51Facades(StatusMessageFacade.DISABLED, BulletinFacade.DISABLED, DeathLogFacade.DISABLED);
}
