package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.Input.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.input.KeyBindings;
import dev.kage.client.core.ui.theme.KageI18n;

/** ADR-190 — cú pháp {@code /phimtat}. */
final class KeyBindingsCoordinatorTest {

    @BeforeAll
    static void installGdx() {
        HeadlessGdx.install(); // KageI18n đọc bundle qua Gdx.files
    }

    @AfterAll
    static void uninstallGdx() {
        HeadlessGdx.uninstall();
    }

    @Test
    void listRebindAndReset() {
        KeyBindings bindings = KeyBindings.load(new KeyBindings.MemoryStore());
        assertThat(KeyBindingsCoordinator.handle(bindings, new String[] {"/phimtat"}, KageI18n.load()))
                .contains("tuido=I");
        assertThat(KeyBindingsCoordinator.handle(bindings, new String[] {"/phimtat", "tuido", "f5"}, KageI18n.load()))
                .contains("F5");
        assertThat(bindings.resolve(Keys.I)).isEqualTo(Keys.F5);
        assertThat(KeyBindingsCoordinator.handle(bindings, new String[] {"/phimtat", "tuido", "j"}, KageI18n.load()))
                .doesNotStartWith("keybind.");
        assertThat(KeyBindingsCoordinator.handle(bindings, new String[] {"/phimtat", "macdinh"}, KageI18n.load()))
                .doesNotStartWith("keybind.");
        assertThat(bindings.resolve(Keys.I)).isEqualTo(Keys.I);
        assertThat(KeyBindingsCoordinator.handle(bindings, new String[] {"/phimtat", "xyz", "k"}, KageI18n.load()))
                .doesNotStartWith("keybind.");
    }
}
