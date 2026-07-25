package dev.kage.server.gateway;

import dev.kage.engine.scripting.ScriptSandbox;
import dev.kage.engine.scripting.ScriptValidator;
import dev.kage.server.database.ServerDatabaseBootstrap;
import dev.kage.server.world.WorldFacade;
import java.time.Duration;
import java.util.ArrayList;

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

    // Chỉ dùng để PARSE (validateSyntax), không thực thi — giới hạn statement không có ý nghĩa ở
    // đây (khác ScriptSandbox dùng lúc gameplay thật), giữ số dương bất kỳ cho hợp constructor.
    private static final long SCRIPT_VALIDATION_STATEMENT_LIMIT = 1L;

    // SERVER.md §8 gợi ý "định kỳ 5 phút" cho dữ liệu tái tạo được (vị trí, HP) — chưa đo, đủ cho
    // lát cắt persistence này (tinh chỉnh 1 chỗ khi có số liệu thật, giống MOVE_SPEED_MPS/AOI_RADIUS).
    private static final Duration POSITION_SAVE_INTERVAL = Duration.ofMinutes(5);

    private GatewayBootstrap() {
    }

    public static void main(String[] args) throws InterruptedException {
        var databaseBootstrap = new ServerDatabaseBootstrap();
        var facades = wireFacades(databaseBootstrap);

        // SCRIPTING.md §8 — fail fast lúc khởi động, KHÔNG đợi người chơi phát hiện lỗi cú pháp
        // script. Gom script_ref từ DB (quest/dialogue) + hằng số Java (skill, chưa DB-driven).
        var scriptRefs = new ArrayList<String>();
        scriptRefs.addAll(databaseBootstrap.listContentScriptRefs());
        scriptRefs.addAll(WorldFacade.listSkillScriptResources());
        new ScriptValidator(new ScriptSandbox(SCRIPT_VALIDATION_STATEMENT_LIMIT)).validateAll(scriptRefs);

        // characterRegistry là WorldOutboundSink thật — PHẢI tồn tại trước WorldFacade vì
        // WorldFacade cần sink lúc construct (SERVER.md §2: kết quả tick đi qua outbound queue).
        var characterRegistry =
                new ConnectedCharacterRegistry(facades.inventoryFacade(), facades.contentFacades().questFacade());
        var worldFacade = new WorldFacade(characterRegistry);
        worldFacade.start();
        var positionPersister =
                new CharacterPositionPersister(facades.characterFacade(), characterRegistry, POSITION_SAVE_INTERVAL);
        positionPersister.start();
        var dungeonInstanceRegistry = new DungeonInstanceRegistry(
                characterRegistry, facades.characterFacade(), facades.contentFacades().socialFacades().partyFacade(),
                worldFacade);
        var worldGateway = new WorldGateway(worldFacade, characterRegistry, positionPersister, dungeonInstanceRegistry);

        var server = new GatewayServer(PORT, facades, worldGateway);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Thứ tự SERVER.md §10: ngừng nhận kết nối mới trước (server.stop() đóng channel),
            // rồi mới dừng world tick + đóng DB. positionPersister dừng SAU worldFacade — vòng
            // quét định kỳ cuối cùng vẫn nên chạy trước khi tắt hẳn (không mất vị trí ai đó vừa
            // logout đúng lúc server tắt). dungeonInstanceRegistry dừng CÙNG lúc worldFacade (mọi
            // World, chính lẫn dungeon, dừng trước khi đóng DB).
            server.stop();
            worldFacade.stop();
            dungeonInstanceRegistry.stopAll();
            characterRegistry.stop();
            positionPersister.stop();
            databaseBootstrap.shutdown();
        }, "gateway-shutdown"));
        server.awaitTermination();
    }

    /** Tách khỏi {@code main()} để giữ dưới giới hạn Checkstyle MethodLength — càng thêm facade, dòng càng dài. */
    private static GatewayFacades wireFacades(ServerDatabaseBootstrap databaseBootstrap) {
        var accountFacade = databaseBootstrap.wireAccountFacade();
        var characterFacade = databaseBootstrap.wireCharacterFacade();
        var inventoryFacade = databaseBootstrap.wireInventoryFacade();
        var shopFacade = databaseBootstrap.wireShopFacade();
        var dialogueFacade = databaseBootstrap.wireDialogueFacade();
        var questFacade = databaseBootstrap.wireQuestFacade();
        var friendFacade = databaseBootstrap.wireFriendFacade();
        var mailFacade = databaseBootstrap.wireMailFacade();
        var chatFacade = databaseBootstrap.wireChatFacade();
        var guildFacade = databaseBootstrap.wireGuildFacade();
        var partyFacade = databaseBootstrap.wirePartyFacade();
        var tradeFacade = databaseBootstrap.wireTradeFacade();

        var socialFacades =
                new SocialFacades(friendFacade, mailFacade, chatFacade, guildFacade, partyFacade, tradeFacade);
        var contentFacades = new ContentFacades(shopFacade, dialogueFacade, questFacade, socialFacades);
        return new GatewayFacades(accountFacade, characterFacade, inventoryFacade, contentFacades);
    }
}
