package dev.kage.client.desktop;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import dev.kage.client.core.screen.LoginScreen;
import dev.kage.client.core.world.PlayContext;
import dev.kage.engine.core.KageEngine;
import dev.kage.engine.ecs.EcsWorld;

/**
 * Launcher Desktop — CHỈ chứa khởi động + platform adapter, KHÔNG gameplay (Điều 28,
 * CLIENT.md §2). Mở cửa sổ NGAY vào {@link LoginScreen} — kết nối server-gateway + đăng
 * nhập/chọn nhân vật giờ là luồng async bên trong chuỗi màn hình Login → CharacterSelect → World
 * (CLIENT.md §8: không block render thread), không còn chặn trước khi cửa sổ mở như bản cũ.
 *
 * <p>{@code args[0]} (tuỳ chọn) = username điền sẵn ô Login, KHÔNG bắt buộc cho lần chạy bình
 * thường (khác bản cũ — trước đây là tham số bắt buộc để phân biệt 2 client trên cùng máy).
 */
public final class DesktopLauncher {

    /** Tương đối so với thư mục người dùng ({@code FileType.External} trên desktop). */
    private static final String PREFERENCES_DIRECTORY = ".kage/";

    private DesktopLauncher() {}

    public static void main(String[] args) throws Exception {
        String initialUsername = args.length > 0 ? args[0] : null;

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Project Kage");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        // Foreground FPS không giới hạn tick logic — logic luôn cố định 60Hz (ENGINE.md §4).
        config.setForegroundFPS(144);
        // Cài đặt người chơi (hiện chỉ có ngôn ngữ — GdxPreferencesLanguageStore) nằm cạnh
        // PlatformAdapter#savePath (~/.kage), thay vì ~/.prefs/ mặc định của libGDX.
        config.setPreferencesConfig(PREFERENCES_DIRECTORY, Files.FileType.External);

        DesktopPlatformAdapter platform = new DesktopPlatformAdapter();
        DesktopInputMapper inputMapper = new DesktopInputMapper();

        KageEngine engine = new KageEngine(
                platform,
                kageEngine -> new LoginScreen(
                        kageEngine,
                        new PlayContext(kageEngine.services().get(EcsWorld.class), inputMapper),
                        initialUsername));

        new Lwjgl3Application(engine, config);
    }
}
