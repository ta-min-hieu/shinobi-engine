package dev.kage.engine.core;

import java.nio.file.Path;

/**
 * Điểm nối duy nhất giữa gameplay và platform cụ thể (ENGINE.md §8, Điều 28).
 *
 * <p>Gameplay code (client-core) chỉ thấy interface này — KHÔNG BAO GIỜ kiểm tra
 * {@code Gdx.app.getType() == ApplicationType.Android} trực tiếp. Mỗi launcher
 * (client-desktop, client-android...) cung cấp một implementation.
 */
public interface PlatformAdapter {

    /** Thư mục lưu file cấu hình/save cục bộ — khác nhau giữa Desktop và Android. */
    Path savePath();

    InputType inputType();

    /** Hệ số scale UI theo DPI thiết bị (UI.md §6). 1.0 = không scale. */
    float uiScale();

    void openUrl(String url);

    /** No-op trên desktop; rung thiết bị thật trên Android. */
    void vibrate(int millis);

    enum InputType { KEYBOARD_MOUSE, TOUCH }
}
