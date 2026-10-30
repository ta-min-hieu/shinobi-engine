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
        MerchantCommands merchant) {

    static ExpansionCommands of(GatewayFacades facades, WorldGateway worldGateway, NpcProximity proximity) {
        ExpansionFacades expansion = facades.progression().expansion();
        Clock clock = Clock.systemUTC();
        return new ExpansionCommands(
                new SecurityPinCommands(expansion.securityPin(), clock),
                new BuyOrderCommands(expansion.buyOrders(), worldGateway.registry()),
                new RevivalCommands(expansion.consumables()),
                new MerchantCommands(
                        expansion.merchant(), facades.progression().adventure().waypoints(), proximity, clock));
    }
}
