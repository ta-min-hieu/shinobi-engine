package dev.kage.client.desktop;

import java.util.concurrent.ThreadLocalRandom;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import dev.kage.client.core.net.GameConnection;
import dev.kage.client.core.net.NetworkBootstrap;
import dev.kage.client.core.world.WorldScreen;
import dev.kage.engine.core.KageEngine;
import dev.kage.engine.ecs.EcsWorld;

/**
 * Launcher Desktop — CHỈ chứa khởi động + platform adapter, KHÔNG gameplay (Điều 28,
 * CLIENT.md §2). Từ Phase 3 (movement): kết nối server-gateway + đăng nhập/chọn nhân vật TRƯỚC
 * khi mở cửa sổ game — xem {@link NetworkBootstrap} vì sao block ở đây là chấp nhận được.
 *
 * <p>{@code args[0]} (tuỳ chọn) = username — chạy 2 lần với username khác nhau để tự verify "2
 * client thấy nhau di chuyển" (ROADMAP Phase 3 Exit Criteria) trên cùng máy.
 */
public final class DesktopLauncher {

    private static final String GATEWAY_HOST = "127.0.0.1";
    private static final int GATEWAY_PORT = 7777;

    private DesktopLauncher() {}

    public static void main(String[] args) throws Exception {
        String username = args.length > 0
                ? args[0]
                : "player" + ThreadLocalRandom.current().nextInt(1000, 9999);

        GameConnection connection = new GameConnection(GATEWAY_HOST, GATEWAY_PORT);
        connection.start();
        // Chưa có màn hình Login/CharacterSelect Scene2D thật (NetworkBootstrap javadoc) — nếu
        // handshake lỗi (server chưa chạy, username không hợp lệ...), thoát thẳng với stack
        // trace thay vì UI báo lỗi, đủ cho lát cắt dev/test hiện tại.
        long characterId = NetworkBootstrap.connectAndSelectCharacter(connection, username);

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project Kage — " + username + " (characterId=" + characterId + ")");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        // Foreground FPS không giới hạn tick logic — logic luôn cố định 60Hz (ENGINE.md §4).
        config.setForegroundFPS(144);

        DesktopPlatformAdapter platform = new DesktopPlatformAdapter();
        DesktopInputMapper inputMapper = new DesktopInputMapper();

        KageEngine engine = new KageEngine(
                platform,
                kageEngine -> new WorldScreen(kageEngine.services().get(EcsWorld.class), inputMapper, connection));

        new Lwjgl3Application(engine, config);
    }
}
