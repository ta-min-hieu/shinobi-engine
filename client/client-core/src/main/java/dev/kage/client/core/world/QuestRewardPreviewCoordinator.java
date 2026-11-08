package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetQuestRewardPreviewRequest;
import dev.kage.protocol.GetQuestRewardPreviewResponse;
import dev.kage.protocol.ItemRewardView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.QuestRewardPreviewOutcome;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Xem trước thưởng nhiệm vụ (ADR-242) — {@code /xemthuong <mã nhiệm vụ>} (hoặc {@code /rewards}): EXP, linh thạch và
 * vật phẩm sẽ nhận theo tiến độ hiện tại.
 */
final class QuestRewardPreviewCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/xemthuong";
    private static final String COMMAND_EN = "/rewards";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    QuestRewardPreviewCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Hỏi thưởng của {@code questCode} — cũng dùng được từ nhật ký nhiệm vụ. */
    void preview(String questCode) {
        sender.sendReadRequest(
                Opcode.OPCODE_GET_QUEST_REWARD_PREVIEW_REQUEST_VALUE,
                GetQuestRewardPreviewRequest.newBuilder()
                        .setQuestCode(questCode)
                        .build());
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        if (words.length < 2 || words[1].isBlank()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "questpreview.usage"));
            return true;
        }
        preview(words[1].strip());
        return true;
    }

    /** Liệt kê vật phẩm thưởng: {@code mã x số, …}; rỗng ⇒ chuỗi rỗng. Tĩnh để test. */
    static String itemList(java.util.List<ItemRewardView> items) {
        StringBuilder out = new StringBuilder();
        for (ItemRewardView item : items) {
            if (!out.isEmpty()) {
                out.append(", ");
            }
            out.append(item.getItemTemplateCode()).append(" x").append(item.getQuantity());
        }
        return out.toString();
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_QUEST_REWARD_PREVIEW_RESPONSE_VALUE) {
            return false;
        }
        GetQuestRewardPreviewResponse response = GetQuestRewardPreviewResponse.parseFrom(frame.payload());
        if (response.getOutcome() != QuestRewardPreviewOutcome.QUEST_REWARD_PREVIEW_OUTCOME_OK) {
            boolean missing = response.getOutcome() == QuestRewardPreviewOutcome.QUEST_REWARD_PREVIEW_OUTCOME_NOT_FOUND;
            chat.showSystemNotice(
                    KageI18n.textOrKey(i18n, missing ? "questpreview.not_found" : "questpreview.unavailable"));
            return true;
        }
        String items = itemList(response.getItemsList());
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n,
                "questpreview.line",
                response.getQuestCode(),
                response.getExp(),
                response.getGold(),
                items.isEmpty() ? KageI18n.textOrKey(i18n, "questpreview.no_items") : items));
        return true;
    }
}
