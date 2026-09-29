package dev.kage.client.core.screen;

import dev.kage.client.core.net.GameConnection;
import dev.kage.client.core.world.PlayContext;
import dev.kage.client.core.world.WorldScreen;
import dev.kage.engine.core.KageEngine;
import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.SelectCharacterResponse;

/**
 * Chỗ DUY NHẤT dựng {@link WorldScreen} và nối hai lối ra của nó — dùng cho cả lần vào đầu tiên (từ
 * {@link CharacterSelectScreen}) lẫn mỗi lần tự vào lại sau khi đứt mạng, để hai đường không bao giờ
 * nối khác nhau.
 *
 * <ul>
 *   <li><b>Phiên kết thúc</b> (bị đá vì đăng nhập nơi khác 2026-09-25, hoặc tự vào lại thất bại) ⇒
 *       về {@link LoginScreen} kèm lý do, ô tên điền sẵn.
 *   <li><b>Tự vào lại thành công</b> (quyết định chủ dự án 2026-09-29) ⇒ dựng một màn world MỚI
 *       trên cùng kết nối, cho cùng nhân vật, bằng đúng hàm này.
 * </ul>
 *
 * <p>Tên đăng nhập chỉ sống trong bộ nhớ của tiến trình (giao thức không có mật khẩu hay token trình
 * lại được — ADR-017), không ghi ra đĩa.
 */
final class WorldEntry {

    private final KageEngine engine;
    private final PlayContext context;
    private final GameConnection connection;
    private final String username;

    WorldEntry(KageEngine engine, PlayContext context, GameConnection connection, String username) {
        this.engine = engine;
        this.context = context;
        this.connection = connection;
        this.username = username;
    }

    /** Dựng màn world cho {@code character} rồi chuyển sang nó. */
    void open(CharacterSummary character, SelectCharacterResponse enterWorld) {
        WorldScreen worldScreen = new WorldScreen(context, connection, character, enterWorld);
        // Màn world đã tự dừng kết nối trước khi gọi tới đây.
        worldScreen.setOnSessionEnded(errorKey -> {
            LoginScreen login = new LoginScreen(engine, context, username);
            login.showSessionEndedReason(errorKey);
            engine.setScreen(login);
        });
        worldScreen.enableAutoRelog(username, this::open);
        engine.setScreen(worldScreen);
    }
}
