package dev.kage.server.gateway;

import dev.kage.character.api.PlayerLookupFacade;
import dev.kage.mail.api.MailArchiveFacade;

/** Facade lô 53 (ADR-351…) gom thành MỘT thành phần của {@link Lot52Facades}. */
record Lot53Facades(PlayerLookupFacade lookup, MailArchiveFacade mailArchive) {

    /** Composition không có các tính năng này (test). */
    static final Lot53Facades DISABLED = new Lot53Facades(PlayerLookupFacade.DISABLED, MailArchiveFacade.DISABLED);
}
