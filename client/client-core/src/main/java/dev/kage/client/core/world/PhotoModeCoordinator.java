package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Chế độ chụp ảnh (ADR-244) — phím F10 (hoặc lệnh {@code /chupanh}, {@code /photo}) ẩn toàn bộ HUD và khung chat để
 * chụp màn hình; F10 lần nữa hiện lại. Thuần client: thế giới vẫn chạy, mạng vẫn nhận gói.
 */
final class PhotoModeCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/chupanh";
    private static final String COMMAND_EN = "/photo";

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final KeyPressEdge toggleKey = new KeyPressEdge();
    private boolean hidingHud;

    PhotoModeCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /**
     * Mỗi khung — đọc phím bật/tắt; trả {@code true} nếu khung này KHÔNG vẽ HUD. Lúc ẩn không ô nhập nào hiện nên
     * phím luôn được tính.
     */
    boolean poll(boolean toggleHeld, boolean keyboardFree) {
        if (toggleKey.update(toggleHeld, keyboardFree || hidingHud)) {
            toggle();
        }
        return hidingHud;
    }

    /** Mỗi khung — đọc phím rồi đồng bộ HUD ({@code syncHud}) và vẽ mọi cửa sổ nếu không đang ẩn. */
    void renderUnlessHidden(boolean toggleHeld, boolean keyboardFree, Runnable syncHud, PanelHub panels) {
        if (!poll(toggleHeld, keyboardFree)) {
            syncHud.run();
            panels.render(com.badlogic.gdx.Gdx.graphics.getDeltaTime());
        }
    }

    /** Đang ẩn HUD. */
    boolean hidingHud() {
        return hidingHud;
    }

    private void toggle() {
        if (!hidingHud) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "photo.on"));
        }
        hidingHud = !hidingHud;
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        toggle();
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) {
        return false;
    }
}
