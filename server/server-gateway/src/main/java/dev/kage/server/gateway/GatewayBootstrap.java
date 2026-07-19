package dev.kage.server.gateway;

/**
 * Entrypoint {@code main()} mỏng — logic thật ở {@link GatewayServer} (tách ra để test end-to-end
 * bằng socket thật được, xem {@code GatewayServerIntegrationTest}).
 */
public final class GatewayBootstrap {

    private static final int PORT = 7777;

    private GatewayBootstrap() {
    }

    public static void main(String[] args) throws InterruptedException {
        var server = new GatewayServer(PORT);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "gateway-shutdown"));
        server.awaitTermination();
    }
}
