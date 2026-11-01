package dev.kage.client.core.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

/** ADR-143 — công tắc hiển thị: mặc định bật, đảo được, sống qua lần mở game sau, công tắc hỏi-mỗi-khung theo kịp. */
class DisplaySettingsTest {

    /** Nơi lưu trong bộ nhớ — một "máy người chơi". */
    private static final class MemoryStore implements DisplaySettings.Store {
        final Map<String, Boolean> values = new HashMap<>();

        @Override
        public Optional<Boolean> read(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public void write(String key, boolean value) {
            values.put(key, value);
        }
    }

    @Test
    void shouldStartWithEverythingOn() {
        DisplaySettings settings = DisplaySettings.load(new MemoryStore());

        for (DisplaySettings.Option option : DisplaySettings.Option.values()) {
            assertThat(settings.isOn(option)).isTrue();
        }
    }

    @Test
    void shouldRememberAToggleAcrossLaunches() {
        MemoryStore store = new MemoryStore();
        DisplaySettings.load(store).toggle(DisplaySettings.Option.REALM_TITLES);

        DisplaySettings reopened = DisplaySettings.load(store);

        assertThat(reopened.isOn(DisplaySettings.Option.REALM_TITLES)).isFalse();
        assertThat(reopened.isOn(DisplaySettings.Option.PARALLAX)).isTrue();
    }

    @Test
    void shouldLetARendererSeeTheToggleWithoutAskingAgain() {
        DisplaySettings settings = DisplaySettings.load(new MemoryStore());
        BooleanSupplier damage = settings.switchFor(DisplaySettings.Option.DAMAGE_NUMBERS);

        settings.toggle(DisplaySettings.Option.DAMAGE_NUMBERS);

        assertThat(damage.getAsBoolean()).isFalse();
        assertThat(settings.switchFor(DisplaySettings.Option.DAMAGE_NUMBERS)).isSameAs(damage);
    }
}
