package dev.kage.server.gateway;

import java.time.Clock;

/**
 * Lớp lệnh của lô 23 (ADR-115…126) gom vào MỘT trường của {@code GatewayChannelHandler} — giữ constructor handler
 * dưới trần {@code MethodLength}.
 */
record ExpansionCommands(
        SecurityPinCommands securityPin,
        BuyOrderCommands buyOrders,
        RevivalCommands revival,
        MerchantCommands merchant,
        SpiritFieldCommands spiritField,
        TamingCommands taming,
        ArtifactCommands artifacts,
        CaveCommands cave,
        RenameCommands rename,
        FortuneCommands fortune,
        LuckyWheelCommands luckyWheel,
        BroadcastCommands broadcast,
        MilestoneCommands milestones,
        WeeklyTaskCommands weeklyTasks,
        HonorCommands honor,
        Lot29Commands lot29,
        Lot30Commands lot30,
        Lot31Commands lot31) {

    static ExpansionCommands of(GatewayFacades facades, WorldGateway worldGateway, NpcProximity proximity) {
        ExpansionFacades expansion = facades.progression().expansion();
        Clock clock = Clock.systemUTC();
        return new ExpansionCommands(
                new SecurityPinCommands(expansion.securityPin(), clock),
                new BuyOrderCommands(expansion.buyOrders(), worldGateway.registry()),
                new RevivalCommands(expansion.consumables()),
                new MerchantCommands(
                        expansion.merchant(), facades.progression().adventure().waypoints(), proximity, clock),
                new SpiritFieldCommands(
                        expansion.spiritField(),
                        facades.characterFacade(),
                        facades.contentFacades().socialFacades().friendFacade(),
                        clock),
                new TamingCommands(
                        expansion.taming(),
                        expansion.consumables(),
                        facades.characterFacade(),
                        new PlayerNotices(
                                facades.contentFacades().socialFacades().mailFacade(), worldGateway.registry())),
                new ArtifactCommands(expansion.artifacts(), facades.inventoryFacade(), clock),
                new CaveCommands(new CaveCommands.Deps(
                        expansion.caves(),
                        facades.characterFacade(),
                        facades.contentFacades().socialFacades().friendFacade(),
                        worldGateway.dungeonInstanceRegistry(),
                        worldGateway.registry(),
                        clock)),
                new RenameCommands(expansion.rename(), clock),
                new FortuneCommands(expansion.fortune(), proximity, clock),
                new LuckyWheelCommands(expansion.luckyWheel(), clock),
                new BroadcastCommands(new BroadcastCommands.Deps(
                        facades.contentFacades().socialFacades().chatFacade(),
                        facades.characterFacade(),
                        expansion.consumables(),
                        worldGateway.registry(),
                        clock)),
                new MilestoneCommands(expansion.milestones(), clock),
                new WeeklyTaskCommands(expansion.weeklyTasks(), clock),
                new HonorCommands(expansion.honor(), clock),
                Lot29Commands.of(facades, worldGateway),
                Lot30Commands.of(facades, worldGateway),
                Lot31Commands.of(facades, worldGateway));
    }
}
