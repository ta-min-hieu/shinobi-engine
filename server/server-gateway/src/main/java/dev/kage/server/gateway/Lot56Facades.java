package dev.kage.server.gateway;

import dev.kage.character.api.PvpStatsFacade;
import dev.kage.friend.api.FriendRequestNoteFacade;

/** Facade lô 56 (ADR-375…) gom thành MỘT thành phần của {@link Lot54Facades}. Thư nhóm dùng facade thư sẵn có. */
record Lot56Facades(FriendRequestNoteFacade requestNotes, PvpStatsFacade pvpStats) {

    /** Composition không có các tính năng này (test). */
    static final Lot56Facades DISABLED = new Lot56Facades(FriendRequestNoteFacade.DISABLED, PvpStatsFacade.DISABLED);
}
