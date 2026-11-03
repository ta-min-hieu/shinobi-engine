package dev.kage.server.gateway;

import dev.kage.character.api.ExpBuffFacade;
import dev.kage.inventory.api.BagSortFacade;
import dev.kage.inventory.api.GemCombineFacade;
import dev.kage.inventory.api.ItemLockFacade;
import dev.kage.inventory.api.LuckyBagFacade;
import dev.kage.world.api.DungeonSweepFacade;

/** Facade lô 29 (ADR-159…) gom thành MỘT thành phần của {@link ExpansionFacades}. */
record Lot29Facades(
        ItemLockFacade itemLock,
        BagSortFacade bagSort,
        GemCombineFacade gemCombine,
        DungeonSweepFacade dungeonSweep,
        LuckyBagFacade luckyBag,
        ExpBuffFacade expBuff) {

    /** Composition không có các tính năng này (test). */
    static final Lot29Facades DISABLED = new Lot29Facades(
            ItemLockFacade.DISABLED,
            BagSortFacade.DISABLED,
            GemCombineFacade.DISABLED,
            DungeonSweepFacade.DISABLED,
            LuckyBagFacade.DISABLED,
            ExpBuffFacade.DISABLED);
}
