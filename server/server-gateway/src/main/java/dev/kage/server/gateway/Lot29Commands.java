package dev.kage.server.gateway;

import dev.kage.protocol.SortInventoryResponse;

/** Lớp lệnh lô 29 (ADR-159…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot29Commands(
        ItemLockCommands itemLock,
        GemCombineCommands gemCombine,
        DungeonSweepCommands dungeonSweep,
        LuckyBagCommands luckyBag,
        Lot29Facades facades) {

    static Lot29Commands of(GatewayFacades gateway, WorldGateway worldGateway) {
        Lot29Facades facades = gateway.progression().expansion().lot29();
        return new Lot29Commands(
                new ItemLockCommands(facades.itemLock()),
                new GemCombineCommands(facades.gemCombine()),
                new DungeonSweepCommands(new DungeonSweepCommands.Deps(
                        facades.dungeonSweep(),
                        gateway.inventoryFacade(),
                        gateway.characterFacade(),
                        worldGateway.registry(),
                        new PlayerNotices(
                                gateway.contentFacades().socialFacades().mailFacade(), worldGateway.registry()))),
                new LuckyBagCommands(facades.luckyBag()),
                facades);
    }

    /** ADR-160 — Virtual Thread; {@code characterId} từ session. */
    SortInventoryResponse sortInventory(long characterId) {
        int merged = facades.bagSort().sort(characterId);
        return SortInventoryResponse.newBuilder()
                .setOk(true)
                .setMergedRows(merged)
                .build();
    }
}
