package dev.kage.server.gateway;

import dev.kage.achievement.api.AchievementShareFacade;
import dev.kage.character.api.LifetimeStatsFacade;
import dev.kage.character.api.StarterGiftFacade;
import dev.kage.quest.api.QuestCatalogFacade;

/**
 * Facade lô 37 (ADR-223…) gom thành MỘT thành phần của {@link Lot36Facades} (lồng nhau để {@code
 * GatewayBootstrap.expansionFacades} khỏi chạm trần {@code MethodLength}).
 */
record Lot37Facades(
        LifetimeStatsFacade lifetime,
        QuestCatalogFacade questCatalog,
        AchievementShareFacade achievementShare,
        StarterGiftFacade starterGift,
        Lot38Facades lot38) {

    /** Composition không có các tính năng này (test). */
    static final Lot37Facades DISABLED = new Lot37Facades(
            LifetimeStatsFacade.DISABLED,
            QuestCatalogFacade.DISABLED,
            AchievementShareFacade.DISABLED,
            StarterGiftFacade.DISABLED,
            Lot38Facades.DISABLED);
}
