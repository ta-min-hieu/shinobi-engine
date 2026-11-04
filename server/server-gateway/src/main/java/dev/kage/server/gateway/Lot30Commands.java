package dev.kage.server.gateway;

/** Lớp lệnh lô 30 (ADR-167…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot30Commands(MailBulkCommands mailBulk) {

    static Lot30Commands of(GatewayFacades gateway, WorldGateway worldGateway) {
        return new Lot30Commands(
                new MailBulkCommands(gateway.contentFacades().socialFacades().mailFacade()));
    }
}
