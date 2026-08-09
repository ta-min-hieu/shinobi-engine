package dev.kage.client.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;

import dev.kage.client.core.input.GameAction;
import dev.kage.client.core.input.InputMapper;

/**
 * Ánh xạ bàn phím -> {@link GameAction} (CLIENT.md §5 bảng ánh xạ mặc định). Đây là NƠI DUY NHẤT
 * biết phím cụ thể — gameplay code (client-core) chỉ thấy {@link InputMapper}.
 */
final class DesktopInputMapper implements InputMapper {

    @Override
    public boolean isPressed(GameAction action) {
        return switch (action) {
            case MOVE_LEFT -> Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT);
            case MOVE_RIGHT -> Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT);
            // ADR-033 — phím Lên/W giờ là NHẢY thay vì di chuyển (đổi từ MOVE_UP), thêm Space
            // (chuẩn platformer, Ninja School Online dùng cả 2).
            case JUMP ->
                Gdx.input.isKeyPressed(Keys.W) || Gdx.input.isKeyPressed(Keys.UP) || Gdx.input.isKeyPressed(Keys.SPACE);
        };
    }
}
