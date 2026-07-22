package dev.kage.server.gateway;

import dev.kage.server.database.ServerDatabaseBootstrap;
import dev.kage.server.world.WorldFacade;
import java.time.Duration;

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

    // SERVER.md §8 gợi ý "định kỳ 5 phút" cho dữ liệu tái tạo được (vị trí, HP) — chưa đo, đủ cho
    // lát cắt persistence này (tinh chỉnh 1 chỗ khi có số liệu thật, giống MOVE_SPEED_MPS/AOI_RADIUS).
    private static final Duration POSITION_SAVE_INTERVAL = Duration.ofMinutes(5);

    private GatewayBootstrap() {
    }

    public static void main(String[] args) throws InterruptedException {
        var databaseBootstrap = new ServerDatabaseBootstrap();
        var accountFacade = databaseBootstrap.wireAccountFacade();
        var characterFacade = databaseBootstrap.wireCharacterFacade();
        var inventoryFacade = databaseBootstrap.wireInventoryFacade();

        // characterRegistry là WorldOutboundSink thật — PHẢI tồn tại trước WorldFacade vì
        // WorldFacade cần sink lúc construct (SERVER.md §2: kết quả tick đi qua outbound queue).
        var characterRegistry = new ConnectedCharacterRegistry(inventoryFacade);
        var worldFacade = new WorldFacade(characterRegistry);
        worldFacade.start();
        var positionPersister =
                new CharacterPositionPersister(characterFacade, characterRegistry, POSITION_SAVE_INTERVAL);
        positionPersister.start();
        var worldGateway = new WorldGateway(worldFacade, characterRegistry, positionPersister);

        var facades = new GatewayFacades(accountFacade, characterFacade, inventoryFacade);
        var server = new GatewayServer(PORT, facades, worldGateway);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Thứ tự SERVER.md §10: ngừng nhận kết nối mới trước (server.stop() đóng channel),
            // rồi mới dừng world tick + đóng DB. positionPersister dừng SAU worldFacade — vòng
            // quét định kỳ cuối cùng vẫn nên chạy trước khi tắt hẳn (không mất vị trí ai đó vừa
            // logout đúng lúc server tắt).
            server.stop();
            worldFacade.stop();
            characterRegistry.stop();
            positionPersister.stop();
            databaseBootstrap.shutdown();
        }, "gateway-shutdown"));
        server.awaitTermination();
    }
}
