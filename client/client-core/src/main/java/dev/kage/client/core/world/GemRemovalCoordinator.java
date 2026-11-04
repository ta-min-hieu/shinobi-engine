package dev.kage.client.core.world;

import java.util.Locale;
import java.util.function.IntConsumer;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.UnsocketGemOutcome;
import dev.kage.protocol.UnsocketGemResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tháo Linh Ngọc (ADR-168) — nút "Tháo ngọc" nằm trong cửa sổ khảm ({@link GemCoordinator}); lớp này nhận kết quả, in
 * một dòng hệ thống, báo máu tối đa mới (món đang mặc) và hỏi lại túi.
 */
final class GemRemovalCoordinator implements WindowFlow {

    private static final String OUTCOME_PREFIX = "UNSOCKET_GEM_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final ItemCatalogCoordinator itemCatalog;
    private final IntConsumer onOwnMaxHpChanged;
    private final I18NBundle i18n = KageI18n.load();

    GemRemovalCoordinator(WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.sender = sender;
        this.chat = feeds.chat();
        this.itemCatalog = feeds.itemCatalog();
        this.onOwnMaxHpChanged = feeds.onOwnMaxHpChanged();
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_UNSOCKET_GEM_RESPONSE_VALUE) {
            return false;
        }
        UnsocketGemResponse response = UnsocketGemResponse.parseFrom(frame.payload());
        chat.showSystemNotice(describe(response));
        if (response.getOutcome() == UnsocketGemOutcome.UNSOCKET_GEM_OUTCOME_OK) {
            if (response.getMaxHp() > 0) {
                onOwnMaxHpChanged.accept(response.getMaxHp());
            }
            sender.listInventory();
        }
        return true;
    }

    /** Dòng hệ thống cho một phản hồi — tách ra để test không cần màn hình. */
    String describe(UnsocketGemResponse response) {
        UnsocketGemOutcome outcome = response.getOutcome();
        if (outcome == UnsocketGemOutcome.UNSOCKET_GEM_OUTCOME_OK) {
            String gem = itemCatalog
                    .nameKey(response.getGemItemTemplateId())
                    .map(key -> KageI18n.textOrKey(i18n, key))
                    .orElseGet(() -> "#" + response.getGemItemTemplateId());
            return KageI18n.formatOrKey(i18n, "gem.unsocket.ok", gem, response.getFee());
        }
        String suffix = outcome == UnsocketGemOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.textOrKey(i18n, "gem.unsocket." + suffix);
    }
}
