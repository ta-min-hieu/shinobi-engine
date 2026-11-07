package dev.kage.client.core.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Locale;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.I18NBundle;
import org.junit.jupiter.api.Test;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.DeleteCharacterOutcome;

/** ADR-205 — mọi kết quả xoá nhân vật có câu dịch; nhãn chờ xoá tính đúng ngày / giờ còn lại. */
final class CharacterDeletionTextTest {

    /** Nạp thẳng bundle từ file (màn chọn nhân vật không cần Gdx để định dạng chữ). */
    private static I18NBundle bundle(String language) {
        return I18NBundle.createBundle(
                new FileHandle(new File("src/main/resources/i18n/strings").getAbsoluteFile()), Locale.of(language));
    }

    @Test
    void everyOutcomeIsTranslated_andPendingLabelCountsDown() {
        for (String language : new String[] {"vi", "en"}) {
            I18NBundle i18n = bundle(language);
            for (DeleteCharacterOutcome outcome : DeleteCharacterOutcome.values()) {
                if (outcome == DeleteCharacterOutcome.UNRECOGNIZED) {
                    continue;
                }
                String key = CharacterDeletionText.outcomeKey(outcome);
                assertThat(KageI18n.textOrKey(i18n, key))
                        .as(language + " " + key)
                        .isNotEqualTo(key);
            }
        }
        String label = CharacterDeletionText.pending(bundle("vi"), 1_000L + 2 * 86_400 + 5 * 3600, 1_000L);
        assertThat(label).contains("2").contains("5").doesNotStartWith("charselect.");
    }
}
