package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.Locale;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.I18NBundle;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.GetNextQuestHintResponse;
import dev.kage.protocol.NextQuestHintKind;

/** ADR-229 — đọc danh sách kênh đã ẩn; ADR-228 — dòng gợi ý nhiệm vụ. */
class ChatFilterTest {

    @Test
    void shouldParseSavedHiddenChannels_ignoringUnknownNames() {
        assertThat(ChatFilterCoordinator.parse("WORLD,TRADE,NOPE,"))
                .containsExactly(ChatChannel.CHAT_CHANNEL_WORLD, ChatChannel.CHAT_CHANNEL_TRADE);
        assertThat(ChatFilterCoordinator.parse("")).isEmpty();
        assertThat(ChatFilterCoordinator.NAMES).containsEntry("gd", ChatChannel.CHAT_CHANNEL_TRADE);
    }

    @Test
    void shouldDescribeANewQuestWithItsGiver() {
        I18NBundle vi = I18NBundle.createBundle(
                new FileHandle(new File("src/main/resources/i18n/strings").getAbsoluteFile()), Locale.of("vi"));
        GetNextQuestHintResponse hint = GetNextQuestHintResponse.newBuilder()
                .setKind(NextQuestHintKind.NEXT_QUEST_HINT_KIND_NEW)
                .setQuestCode("kill_training_dummies")
                .setNpcCode("town_elder")
                .build();

        assertThat(NextQuestHintCoordinator.describe(vi, hint))
                .startsWith("Nhiệm vụ mới: Đập Mộc Nhân — gặp Trưởng lão Chu Hoành");
    }
}
