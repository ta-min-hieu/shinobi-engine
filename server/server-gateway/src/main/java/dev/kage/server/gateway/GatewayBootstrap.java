package dev.kage.server.gateway;

import dev.kage.server.database.ServerDatabaseBootstrap;

/**
 * Entrypoint {@code main()} mỏng — logic thật ở {@link GatewayServer} (tách ra để test end-to-end
 * bằng socket thật được, xem {@code GatewayServerIntegrationTest}).
 *
 * <p>Composition root — nơi DUY NHẤT server-gateway chạm tới {@link ServerDatabaseBootstrap}, và
 * chỉ để lấy {@code AccountFacade} đã lắp ráp sẵn (không bao giờ thấy DataSource/JedisPool trực
 * tiếp — xem javadoc {@link ServerDatabaseBootstrap}, Điều 23).
 */
public final class GatewayBootstrap {

    private static final int PORT = 7777;

    private GatewayBootstrap() {
    }

    public static void main(String[] args) throws InterruptedException {
        var databaseBootstrap = new ServerDatabaseBootstrap();
        var accountFacade = databaseBootstrap.wireAccountFacade();
        var characterFacade = databaseBootstrap.wireCharacterFacade();

        var server = new GatewayServer(PORT, accountFacade, characterFacade);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            databaseBootstrap.shutdown();
        }, "gateway-shutdown"));
        server.awaitTermination();
    }
}
