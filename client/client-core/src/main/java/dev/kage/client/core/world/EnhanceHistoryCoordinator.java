package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.EnhanceHistoryEntry;
import dev.kage.protocol.GetEnhanceHistoryRequest;
import dev.kage.protocol.GetEnhanceHistoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/** Lịch sử cường hoá (ADR-233) — {@code /lichsucuonghoa} (hoặc {@code /enhancelog}): 20 lần gần nhất vào khung chat. */
final class EnhanceHistoryCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/lichsucuonghoa";
    private static final String COMMAND_EN = "/enhancelog";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    EnhanceHistoryCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Khoá i18n cho kết quả: {@code SUCCESS} ⇒ ok, có {@code DROPPED} ⇒ tụt cấp, còn lại ⇒ hỏng. Tĩnh để test. */
    static String resultKey(String result) {
        String upper = result.toUpperCase(Locale.ROOT);
        if ("SUCCESS".equals(upper)) {
            return "enhancelog.success";
        }
        return upper.contains("DROP") && !upper.contains("NO_DROP") ? "enhancelog.dropped" : "enhancelog.failed";
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_ENHANCE_HISTORY_REQUEST_VALUE, GetEnhanceHistoryRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_ENHANCE_HISTORY_RESPONSE_VALUE) {
            return false;
        }
        GetEnhanceHistoryResponse response = GetEnhanceHistoryResponse.parseFrom(frame.payload());
        if (response.getEntriesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "enhancelog.empty"));
            return true;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "enhancelog.header"));
        for (EnhanceHistoryEntry entry : response.getEntriesList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "enhancelog.line",
                    entry.getItemTemplateId(),
                    entry.getFromLevel(),
                    entry.getToLevel(),
                    entry.getGoldCost(),
                    KageI18n.textOrKey(i18n, resultKey(entry.getResult()))));
        }
        return true;
    }
}
