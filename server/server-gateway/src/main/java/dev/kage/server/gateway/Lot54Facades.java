package dev.kage.server.gateway;

import dev.kage.character.api.ProfileVisitFacade;

/** Facade lô 54 (ADR-359…) gom thành MỘT thành phần của {@link Lot53Facades}. Đọc hết thư dùng facade ADR-314. */
record Lot54Facades(ProfileVisitFacade profileVisits) {

    /** Composition không có các tính năng này (test). */
    static final Lot54Facades DISABLED = new Lot54Facades(ProfileVisitFacade.DISABLED);
}
