package dev.kage.server.gateway;

import dev.kage.account.api.SecurityPinFacade;
import dev.kage.character.api.TribulationFacade;
import dev.kage.chat.api.SectQuiz;
import dev.kage.market.api.BuyOrderFacade;
import dev.kage.pvp.api.TournamentFacade;
import dev.kage.shop.api.TravelingMerchantFacade;
import dev.kage.world.api.ArtifactFacade;
import dev.kage.world.api.ConsumableFacade;
import dev.kage.world.api.EncounterFacade;
import dev.kage.world.api.SpiritFieldFacade;
import dev.kage.world.api.TamingFacade;

/** Lô 23 (ADR-115…126) — gom để {@link ProgressionFacades} chỉ thêm một thành phần. */
record ExpansionFacades(
        SecurityPinFacade securityPin,
        BuyOrderFacade buyOrders,
        ConsumableFacade consumables,
        TravelingMerchantFacade merchant,
        SpiritFieldFacade spiritField,
        EncounterFacade encounters,
        SectQuiz quiz,
        TamingFacade taming,
        TournamentFacade tournament,
        TribulationFacade tribulation,
        ArtifactFacade artifacts) {

    /** Composition không có các tính năng này (test). */
    static final ExpansionFacades DISABLED = new ExpansionFacades(
            SecurityPinFacade.DISABLED,
            BuyOrderFacade.DISABLED,
            ConsumableFacade.DISABLED,
            TravelingMerchantFacade.DISABLED,
            SpiritFieldFacade.DISABLED,
            EncounterFacade.DISABLED,
            SectQuiz.DISABLED,
            TamingFacade.DISABLED,
            TournamentFacade.DISABLED,
            TribulationFacade.DISABLED,
            ArtifactFacade.DISABLED);
}
