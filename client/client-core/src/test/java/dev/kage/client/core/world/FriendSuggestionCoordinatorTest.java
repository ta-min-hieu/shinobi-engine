package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Locale;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.I18NBundle;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.FriendSuggestion;
import dev.kage.protocol.SuggestFriendsResponse;

/** ADR-211 — dòng gợi ý kết bạn: tên kèm cảnh giới, hoặc câu "không có ai". */
class FriendSuggestionCoordinatorTest {

    private static final I18NBundle VI = I18NBundle.createBundle(
            new FileHandle(new File("src/main/resources/i18n/strings").getAbsoluteFile()), Locale.of("vi"));

    @Test
    void shouldListNamesWithRealm_orSayNobody() {
        SuggestFriendsResponse two = SuggestFriendsResponse.newBuilder()
                .addSuggestions(FriendSuggestion.newBuilder().setName("Lam").setLevel(1))
                .addSuggestions(FriendSuggestion.newBuilder().setName("Tuyet").setLevel(12))
                .build();

        assertThat(FriendSuggestionCoordinator.describe(VI, two))
                .startsWith("Gợi ý kết bạn: Lam (")
                .contains("Tuyet (")
                .endsWith("Gõ /ketban <tên> để gửi lời mời.");
        assertThat(FriendSuggestionCoordinator.describe(VI, SuggestFriendsResponse.getDefaultInstance()))
                .startsWith("Chưa có ai");
    }
}
