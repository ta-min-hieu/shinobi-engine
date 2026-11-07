package dev.kage.client.core.input;

import static org.assertj.core.api.Assertions.assertThat;

import com.badlogic.gdx.Input.Keys;
import org.junit.jupiter.api.Test;

/** ADR-190 — đổi phím, chặn trùng / phím dành riêng, lưu và đọc lại, trả mặc định. */
final class KeyBindingsTest {

    @Test
    void rebindRemapsTheDefaultKeyAndPersists() {
        KeyBindings.MemoryStore store = new KeyBindings.MemoryStore();
        KeyBindings bindings = KeyBindings.load(store);
        assertThat(bindings.resolve(Keys.I)).isEqualTo(Keys.I);

        assertThat(bindings.rebind(KeyBindings.Binding.INVENTORY, Keys.F5)).isEqualTo(KeyBindings.Rebind.OK);
        assertThat(bindings.resolve(Keys.I)).isEqualTo(Keys.F5);
        assertThat(bindings.resolve(Keys.J)).isEqualTo(Keys.J); // phím khác không đổi
        assertThat(KeyBindings.load(store).resolve(Keys.I)).isEqualTo(Keys.F5); // đọc lại từ nơi lưu
    }

    @Test
    void refusesKeysInUseReservedOrUnknown() {
        KeyBindings bindings = KeyBindings.load(new KeyBindings.MemoryStore());
        assertThat(bindings.rebind(KeyBindings.Binding.INVENTORY, Keys.J)).isEqualTo(KeyBindings.Rebind.KEY_IN_USE);
        assertThat(bindings.rebind(KeyBindings.Binding.INVENTORY, Keys.ESCAPE)).isEqualTo(KeyBindings.Rebind.RESERVED);
        assertThat(bindings.rebind(KeyBindings.Binding.INVENTORY, -1)).isEqualTo(KeyBindings.Rebind.UNKNOWN_KEY);
        assertThat(bindings.resolve(Keys.I)).isEqualTo(Keys.I);
    }

    @Test
    void freedKeyCanBeTakenAndResetRestoresEverything() {
        KeyBindings.MemoryStore store = new KeyBindings.MemoryStore();
        KeyBindings bindings = KeyBindings.load(store);
        bindings.rebind(KeyBindings.Binding.QUEST_LOG, Keys.F6); // J được giải phóng
        assertThat(bindings.rebind(KeyBindings.Binding.INVENTORY, Keys.J)).isEqualTo(KeyBindings.Rebind.OK);
        bindings.resetAll();
        assertThat(bindings.resolve(Keys.I)).isEqualTo(Keys.I);
        assertThat(bindings.resolve(Keys.J)).isEqualTo(Keys.J);
        assertThat(store.read("keybind.tuido")).isEmpty();
    }

    @Test
    void allDefaultKeysAreDistinct() {
        assertThat(java.util.Arrays.stream(KeyBindings.Binding.values())
                        .mapToInt(KeyBindings.Binding::defaultKey)
                        .distinct()
                        .count())
                .isEqualTo(KeyBindings.Binding.values().length);
    }
}
