package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Locale;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.I18NBundle;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.AchievementView;

/** ADR-210 — dòng thành tựu nối chỉ số cộng khi đeo danh hiệu, chỉ những chỉ số khác 0. */
class AchievementBonusTextTest {

    private static final I18NBundle VI = I18NBundle.createBundle(
            new FileHandle(new File("src/main/resources/i18n/strings").getAbsoluteFile()), Locale.of("vi"));

    @Test
    void shouldListOnlyNonZeroStats() {
        AchievementView view =
                AchievementView.newBuilder().setBonusAtk(4).setBonusRes(4).build();

        assertThat(AchievementCoordinator.bonusSuffix(VI, view)).isEqualTo(" (Công +4, Kháng +4)");
        assertThat(AchievementCoordinator.bonusSuffix(VI, AchievementView.getDefaultInstance()))
                .isEmpty();
    }
}
