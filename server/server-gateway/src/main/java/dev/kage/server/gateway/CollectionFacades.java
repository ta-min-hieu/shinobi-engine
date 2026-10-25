package dev.kage.server.gateway;

import dev.kage.achievement.api.BestiaryFacade;
import dev.kage.achievement.api.ReputationFacade;

/** Lô 22 phần 2 — sổ sưu tầm (ADR-108), danh vọng (ADR-109); gom để {@link ProgressionFacades} thêm một thành phần. */
record CollectionFacades(BestiaryFacade bestiary, ReputationFacade reputation) {

    /** Composition không có các tính năng này (test). */
    static final CollectionFacades DISABLED = new CollectionFacades(BestiaryFacade.DISABLED, ReputationFacade.DISABLED);
}
