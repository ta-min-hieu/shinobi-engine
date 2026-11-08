package dev.kage.server.gateway;

import dev.kage.character.api.LifetimeStatsFacade;

/**
 * Facade lô 37 (ADR-223…) gom thành MỘT thành phần của {@link Lot36Facades} (lồng nhau để {@code
 * GatewayBootstrap.expansionFacades} khỏi chạm trần {@code MethodLength}).
 */
record Lot37Facades(LifetimeStatsFacade lifetime) {

    /** Composition không có các tính năng này (test). */
    static final Lot37Facades DISABLED = new Lot37Facades(LifetimeStatsFacade.DISABLED);
}
