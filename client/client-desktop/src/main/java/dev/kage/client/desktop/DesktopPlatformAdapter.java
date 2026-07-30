package dev.kage.client.desktop;

import java.nio.file.Path;

import com.badlogic.gdx.Gdx;

import dev.kage.engine.core.PlatformAdapter;

/** Implementation Desktop của {@link PlatformAdapter} (ENGINE.md §8, CLIENT.md §11). */
final class DesktopPlatformAdapter implements PlatformAdapter {

    @Override
    public Path savePath() {
        return Path.of(System.getProperty("user.home"), ".kage");
    }

    @Override
    public InputType inputType() {
        return InputType.KEYBOARD_MOUSE;
    }

    @Override
    public float uiScale() {
        return 1f;
    }

    @Override
    public void openUrl(String url) {
        Gdx.net.openURI(url);
    }

    @Override
    public void vibrate(int millis) {
        // No-op trên desktop — chỉ Android rung thật (ENGINE.md §8).
    }
}
