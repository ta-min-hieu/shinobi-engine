package dev.kage.server.gateway;

import dev.kage.achievement.api.BestiaryFacade;
import dev.kage.achievement.api.ReputationFacade;
import dev.kage.guild.api.GuildAuctionFacade;
import dev.kage.inventory.api.RefinementFacade;

/**
 * Lô 22 — sổ sưu tầm (ADR-108), danh vọng (ADR-109), tẩy luyện (ADR-111), đấu giá bang (ADR-112); gom để
 * {@link ProgressionFacades} thêm một
 * thành phần.
 */
record CollectionFacades(
        BestiaryFacade bestiary,
        ReputationFacade reputation,
        RefinementFacade refinement,
        GuildAuctionFacade guildAuctions) {

    /** Composition không có các tính năng này (test). */
    static final CollectionFacades DISABLED = new CollectionFacades(
            BestiaryFacade.DISABLED, ReputationFacade.DISABLED, RefinementFacade.DISABLED, GuildAuctionFacade.DISABLED);
}
