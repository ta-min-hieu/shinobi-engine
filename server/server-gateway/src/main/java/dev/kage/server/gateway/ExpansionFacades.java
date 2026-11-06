package dev.kage.server.gateway;

import dev.kage.account.api.SecurityPinFacade;
import dev.kage.character.api.MilestoneFacade;
import dev.kage.character.api.RenameFacade;
import dev.kage.character.api.TribulationFacade;
import dev.kage.chat.api.SectQuiz;
import dev.kage.checkin.api.FortuneFacade;
import dev.kage.checkin.api.LuckyWheelFacade;
import dev.kage.checkin.api.WeeklyTaskFacade;
import dev.kage.market.api.BuyOrderFacade;
import dev.kage.pvp.api.HonorFacade;
import dev.kage.pvp.api.TournamentFacade;
import dev.kage.shop.api.TravelingMerchantFacade;
import dev.kage.world.api.ArtifactFacade;
import dev.kage.world.api.CaveFacade;
import dev.kage.world.api.ConsumableFacade;
import dev.kage.world.api.EncounterFacade;
import dev.kage.world.api.KillingIntentFacade;
import dev.kage.world.api.SpiritFieldFacade;
import dev.kage.world.api.TamingFacade;
import dev.kage.world.api.TreasureFacade;

/** Lô 23 (ADR-115…126) + sát khí (ADR-142) + Tàng Bảo Đồ (ADR-147) — gom để {@link ProgressionFacades} gọn. */
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
        ArtifactFacade artifacts,
        CaveFacade caves,
        KillingIntentFacade killingIntent,
        TreasureFacade treasure,
        RenameFacade rename,
        FortuneFacade fortune,
        LuckyWheelFacade luckyWheel,
        MilestoneFacade milestones,
        WeeklyTaskFacade weeklyTasks,
        HonorFacade honor,
        Lot29Facades lot29,
        Lot30Facades lot30,
        Lot31Facades lot31) {

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
            ArtifactFacade.DISABLED,
            CaveFacade.DISABLED,
            KillingIntentFacade.DISABLED,
            TreasureFacade.DISABLED,
            RenameFacade.DISABLED,
            FortuneFacade.DISABLED,
            LuckyWheelFacade.DISABLED,
            MilestoneFacade.DISABLED,
            WeeklyTaskFacade.DISABLED,
            HonorFacade.DISABLED,
            Lot29Facades.DISABLED,
            Lot30Facades.DISABLED,
            Lot31Facades.DISABLED);
}
