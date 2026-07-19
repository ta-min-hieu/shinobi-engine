package dev.kage.server.gateway;

import dev.kage.server.database.ServerDatabaseBootstrap;
import dev.kage.server.world.WorldFacade;

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

        // characterRegistry là WorldOutboundSink thật — PHẢI tồn tại trước WorldFacade vì
        // WorldFacade cần sink lúc construct (SERVER.md §2: kết quả tick đi qua outbound queue).
        var characterRegistry = new ConnectedCharacterRegistry();
        var worldFacade = new WorldFacade(characterRegistry);
        worldFacade.start();
        var worldGateway = new WorldGateway(worldFacade, characterRegistry);

        var server = new GatewayServer(PORT, accountFacade, characterFacade, worldGateway);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Thứ tự SERVER.md §10: ngừng nhận kết nối mới trước (server.stop() đóng channel),
            // rồi mới dừng world tick + đóng DB.
            server.stop();
            worldFacade.stop();
            databaseBootstrap.shutdown();
        }, "gateway-shutdown"));
        server.awaitTermination();
    }
}
