package dev.kage.server.gateway;

import dev.kage.character.api.NewPlayersFacade;

/**
 * Facade lô 61 (ADR-415…) gom thành MỘT thành phần của {@link Lot59Facades}. Tóm tắt online, bạn lâu vắng, nhắc hộp
 * thư dùng facade bạn bè / bang / lần cuối online / thư sẵn có.
 */
record Lot61Facades(NewPlayersFacade newPlayers) {

    /** Composition không có các tính năng này (test). */
    static final Lot61Facades DISABLED = new Lot61Facades(NewPlayersFacade.DISABLED);
}
