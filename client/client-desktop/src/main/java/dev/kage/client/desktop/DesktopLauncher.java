package dev.kage.client.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import dev.kage.client.core.playground.PlaygroundScreen;
import dev.kage.engine.core.KageEngine;
import dev.kage.engine.ecs.EcsWorld;

/**
 * Launcher Desktop — CHỈ chứa khởi động + platform adapter, KHÔNG gameplay (Điều 28,
 * CLIENT.md §2). Đây là Phase 1: mở cửa sổ, hiển thị {@link PlaygroundScreen} demo di chuyển
 * bằng bàn phím với fixed timestep 60Hz (ROADMAP.md Phase 1).
 */
public final class DesktopLauncher {

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        var config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project Kage");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        // Foreground FPS không giới hạn tick logic — logic luôn cố định 60Hz (ENGINE.md §4).
        config.setForegroundFPS(144);

        var platform = new DesktopPlatformAdapter();
        var inputMapper = new DesktopInputMapper();

        var engine = new KageEngine(
                platform,
                kageEngine -> new PlaygroundScreen(kageEngine.services().get(EcsWorld.class), inputMapper));

        new Lwjgl3Application(engine, config);
    }
}
