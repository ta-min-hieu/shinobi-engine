package dev.kage.client.core.world;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.Deflater;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Chụp màn hình (ADR-300) — nhả phím F12: chép khung hình vừa vẽ (cả HUD, trừ khi đang ở chế độ chụp ảnh ADR-244) ra
 * {@code ~/KageScreenshots/kage-yyyyMMdd-HHmmss.png}. Đọc framebuffer trên luồng render (bắt buộc với GL), mã hoá PNG
 * + ghi đĩa trên Virtual Thread để khung hình không khựng. Báo đường dẫn vào chat.
 */
final class ScreenshotCoordinator implements WindowFlow {

    /** Thư mục con trong thư mục người dùng. */
    static final String FOLDER = "KageScreenshots";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final KeyPressEdge key = new KeyPressEdge();

    ScreenshotCoordinator(ChatCoordinator chat) {
        this.chat = chat;
    }

    /** Mỗi khung, SAU khi vẽ xong mọi thứ — bắt nhả phím rồi chụp. Không cấp phát khi không bấm. */
    void poll(boolean keyHeld) {
        if (key.update(keyHeld, true)) {
            capture();
        }
    }

    private void capture() {
        int width = Gdx.graphics.getBackBufferWidth();
        int height = Gdx.graphics.getBackBufferHeight();
        Pixmap pixmap = Pixmap.createFromFrameBuffer(0, 0, width, height);
        FileHandle file = Gdx.files.external(fileName(LocalDateTime.now(java.time.ZoneId.systemDefault())));
        Thread.ofVirtual().name("screenshot-writer").start(() -> write(pixmap, file));
    }

    private void write(Pixmap pixmap, FileHandle file) {
        try {
            // flipY = true: framebuffer GL có dòng 0 ở ĐÁY màn hình.
            PixmapIO.writePNG(file, pixmap, Deflater.DEFAULT_COMPRESSION, true);
            Gdx.app.postRunnable(() -> chat.showSystemNotice(
                    KageI18n.formatOrKey(i18n, "screenshot.saved", file.file().getAbsolutePath())));
        } catch (GdxRuntimeException e) {
            Gdx.app.error("Screenshot", "Không ghi được ảnh chụp: " + file.path(), e);
            Gdx.app.postRunnable(() -> chat.showSystemNotice(KageI18n.textOrKey(i18n, "screenshot.failed")));
        } finally {
            pixmap.dispose();
        }
    }

    /** {@code KageScreenshots/kage-<giờ>.png}. Tĩnh để test. */
    static String fileName(LocalDateTime at) {
        return FOLDER + "/kage-" + STAMP.format(at) + ".png";
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
