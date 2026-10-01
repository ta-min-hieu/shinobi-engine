package dev.kage.client.core.ui.theme;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Luật lưu/đọc lựa chọn ngôn ngữ (song ngữ Việt–Anh, quyết định chủ dự án 2026-10-01). Thuần Java,
 * nơi lưu là {@link FakeLanguageStore} trong bộ nhớ — bản thật ({@link GdxPreferencesLanguageStore})
 * chỉ là vài dòng gọi libGDX Preferences.
 */
class LanguageSettingsTest {

    /** Nơi lưu giả trong bộ nhớ: nhớ đúng một mã, đếm số lần ghi. */
    private static final class FakeLanguageStore implements LanguageStore {

        private String stored;
        private int writes;

        FakeLanguageStore(String initial) {
            this.stored = initial;
        }

        @Override
        public Optional<String> readLanguageCode() {
            return Optional.ofNullable(stored);
        }

        @Override
        public void writeLanguageCode(String code) {
            stored = code;
            writes++;
        }
    }

    @Test
    void defaultsToVietnameseWhenNothingWasEverSaved() {
        assertThat(LanguageSettings.load(new FakeLanguageStore(null)))
                .isEqualTo(ClientLanguage.VI)
                .isEqualTo(ClientLanguage.DEFAULT);
    }

    @Test
    void readsBackTheSavedLanguage() {
        FakeLanguageStore store = new FakeLanguageStore(null);
        LanguageSettings.save(store, ClientLanguage.EN);

        // Đọc lại từ cùng nơi lưu — mô phỏng lần mở game sau.
        assertThat(LanguageSettings.load(store)).isEqualTo(ClientLanguage.EN);
        assertThat(store.stored).isEqualTo("en");
        assertThat(store.writes).isEqualTo(1);
    }

    @Test
    void switchingBackOverwritesThePreviousChoice() {
        FakeLanguageStore store = new FakeLanguageStore("en");

        LanguageSettings.save(store, ClientLanguage.VI);

        assertThat(LanguageSettings.load(store)).isEqualTo(ClientLanguage.VI);
    }

    @Test
    void garbageOrUnknownCodeFallsBackToVietnamese() {
        // File cấu hình sửa tay, hoặc một bản game sau có thêm ngôn ngữ rồi người chơi quay về bản cũ:
        // màn đăng nhập vẫn phải mở được, bằng ngôn ngữ mặc định.
        for (String stored : new String[] {"", "   ", "fr", "vietnamese", "e n"}) {
            assertThat(LanguageSettings.load(new FakeLanguageStore(stored)))
                    .as("mã đã lưu \"%s\"", stored)
                    .isEqualTo(ClientLanguage.VI);
        }
    }

    @Test
    void codeIsCaseAndWhitespaceInsensitive() {
        assertThat(LanguageSettings.load(new FakeLanguageStore(" EN "))).isEqualTo(ClientLanguage.EN);
    }

    @Test
    void everyLanguageRoundTripsThroughItsCode() {
        for (ClientLanguage language : ClientLanguage.values()) {
            assertThat(ClientLanguage.fromCode(language.code())).contains(language);
            assertThat(language.locale().getLanguage()).isEqualTo(language.code());
            assertThat(language.nameKey()).isEqualTo("language.name." + language.code());
        }
        assertThat(ClientLanguage.fromCode(null)).isEmpty();
    }
}
