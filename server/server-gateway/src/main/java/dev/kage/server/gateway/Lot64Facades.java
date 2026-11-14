package dev.kage.server.gateway;

import dev.kage.inventory.api.GearRankingFacade;
import dev.kage.monster.api.BossKillRankingFacade;
import dev.kage.quest.api.QuestRankingFacade;

/** Facade lô 64 (ADR-439…) gom thành MỘT thành phần của {@link Lot63Facades}. */
record Lot64Facades(GearRankingFacade gear, QuestRankingFacade quests, BossKillRankingFacade bossKills) {

    /** Composition không có các tính năng này (test). */
    static final Lot64Facades DISABLED =
            new Lot64Facades(GearRankingFacade.DISABLED, QuestRankingFacade.DISABLED, BossKillRankingFacade.DISABLED);
}
