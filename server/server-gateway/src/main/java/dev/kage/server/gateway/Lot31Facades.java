package dev.kage.server.gateway;

import dev.kage.inventory.api.EquipmentPresetFacade;

/** Facade lô 31 (ADR-175…) gom thành MỘT thành phần của {@link ExpansionFacades}. */
record Lot31Facades(EquipmentPresetFacade equipmentPresets) {

    /** Composition không có các tính năng này (test). */
    static final Lot31Facades DISABLED = new Lot31Facades(EquipmentPresetFacade.DISABLED);
}
