package dev.kage.server.gateway;

/** Lớp lệnh lô 31 (ADR-175…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot31Commands(EquipmentPresetCommands equipmentPresets, BulkUseCommands bulkUse, Lot31Facades facades) {

    static Lot31Commands of(GatewayFacades gateway, WorldGateway worldGateway) {
        Lot31Facades lot31 = gateway.progression().expansion().lot31();
        return new Lot31Commands(
                new EquipmentPresetCommands(new EquipmentPresetCommands.Deps(
                        lot31.equipmentPresets(), gateway.inventoryFacade(), gateway.characterFacade())),
                new BulkUseCommands(
                        gateway.inventoryFacade(),
                        gateway.progression().expansion().lot29().luckyBag()),
                lot31);
    }
}
