package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ListMailResponse;
import dev.kage.protocol.MailView;

/** Lô 48 phía client — bong bóng chat, lịch sử thông báo, dấu thư chưa đọc, màu độ trễ. */
class Lot48ClientTest {

    @Test
    void bubbleTextIsTrimmed() {
        assertThat(ChatBubbleCoordinator.bubbleText("  xin chào  ")).isEqualTo("xin chào");
        assertThat(ChatBubbleCoordinator.bubbleText("a".repeat(60)))
                .hasSize(ChatBubbleCoordinator.MAX_LENGTH)
                .endsWith("...");
    }

    @Test
    void systemHistoryKeepsNewestThirty() {
        Deque<String> history = new ArrayDeque<>();
        for (int i = 0; i < SystemNoticeHistoryCoordinator.CAPACITY + 4; i++) {
            SystemNoticeHistoryCoordinator.remember(history, "n" + i);
        }
        assertThat(history).hasSize(SystemNoticeHistoryCoordinator.CAPACITY);
        assertThat(history.getFirst()).isEqualTo("n4");
    }

    @Test
    void unreadMailGetsMarkAndForwardStripsIt() {
        MailController controller = new MailController();
        controller.onMailList(ListMailResponse.newBuilder()
                .addMails(MailView.newBuilder().setMailId(1).setSubject("Chào").setUnread(true))
                .addMails(MailView.newBuilder().setMailId(2).setSubject("Cũ"))
                .build());
        List<MailController.Mail> mails = controller.mails();
        assertThat(mails.get(0).subject()).isEqualTo(MailController.UNREAD_MARK + "Chào");
        assertThat(mails.get(1).subject()).isEqualTo("Cũ");
        assertThat(MailForwardCoordinator.stripMarks(MailController.UNREAD_MARK + MailController.PINNED_MARK + "x"))
                .isEqualTo("x");
    }

    @Test
    void latencyColourFollowsPingThresholds() {
        assertThat(dev.kage.client.core.ui.hud.HudBannersAccess.color(50))
                .isNotEqualTo(dev.kage.client.core.ui.hud.HudBannersAccess.color(300));
        assertThat(dev.kage.client.core.ui.hud.HudBannersAccess.color(150)).isInstanceOf(Color.class);
    }
}
