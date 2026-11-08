package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.EnhancePreviewOutcome;
import dev.kage.protocol.GetEnhancePreviewRequest;
import dev.kage.protocol.GetEnhancePreviewResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Dự tính cường hoá (ADR-257) — chọn một món trong túi rồi gõ {@code /dutinh} (hoặc {@code /enhanceinfo}): giá vàng, tỉ
 * lệ thành công và nguy cơ tụt cấp của lần cường hoá kế tiếp, đúng bảng server.
 */
final class EnhancePreviewCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/dutinh";
    private static final String COMMAND_EN = "/enhanceinfo";
    private static final String OUTCOME_PREFIX = "ENHANCE_PREVIEW_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private Supplier<Optional<Long>> selection = Optional::empty;

    EnhancePreviewCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối món đang chọn trong túi. */
    void useSelection(Supplier<Optional<Long>> selection) {
        this.selection = selection;
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        Optional<Long> item = selection.get();
        if (item.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "enhancepreview.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_ENHANCE_PREVIEW_REQUEST_VALUE,
                GetEnhancePreviewRequest.newBuilder().setItemId(item.get()).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_ENHANCE_PREVIEW_RESPONSE_VALUE) {
            return false;
        }
        GetEnhancePreviewResponse preview = GetEnhancePreviewResponse.parseFrom(frame.payload());
        if (preview.getOutcome() != EnhancePreviewOutcome.ENHANCE_PREVIEW_OUTCOME_OK) {
            String name = preview.getOutcome().name();
            String key = name.startsWith(OUTCOME_PREFIX)
                    ? name.substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT)
                    : "unspecified";
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "enhancepreview." + key));
            return true;
        }
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n,
                "enhancepreview.line",
                preview.getCurrentLevel(),
                preview.getCurrentLevel() + 1,
                preview.getGoldCost(),
                preview.getSuccessPercent()));
        chat.showSystemNotice(
                preview.getDropsOnFail()
                        ? KageI18n.formatOrKey(i18n, "enhancepreview.drop_risk", preview.getProtectionCharms())
                        : KageI18n.textOrKey(i18n, "enhancepreview.safe"));
        return true;
    }
}
