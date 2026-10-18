package dev.kage.server.gateway;

import dev.kage.checkin.api.CheckInFacade;

/**
 * Facade của các tính năng gắn bó lô 16 (2026-10-18) — gom để {@link GatewayFacades} không phình thêm
 * một thành phần mỗi tính năng.
 */
record ProgressionFacades(CheckInFacade checkInFacade) {

    /** Composition không có các tính năng này (test). */
    static final ProgressionFacades DISABLED = new ProgressionFacades(CheckInFacade.DISABLED);
}
