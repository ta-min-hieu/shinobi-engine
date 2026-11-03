package dev.kage.server.gateway;

import dev.kage.checkin.api.ActivityFacade;
import dev.kage.gm.api.CompensationFacade;
import dev.kage.gm.api.GiftCodeFacade;
import dev.kage.gm.api.PlayerReportFacade;
import dev.kage.gm.api.ServerNoticeFacade;

/** Lô 22 phần 1 (ADR-103..106) — gom để {@link ProgressionFacades} chỉ thêm một thành phần. */
record ServiceDeskFacades(
        ServerNoticeFacade notice,
        GiftCodeFacade giftCodes,
        PlayerReportFacade reports,
        ActivityFacade activity,
        CompensationFacade compensations) {

    /** Composition không có các tính năng này (test). */
    static final ServiceDeskFacades DISABLED = new ServiceDeskFacades(
            ServerNoticeFacade.DISABLED,
            GiftCodeFacade.DISABLED,
            PlayerReportFacade.DISABLED,
            ActivityFacade.DISABLED,
            CompensationFacade.DISABLED);
}
