package dev.kage.client.core.world;

import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetItemLockOutcome;
import dev.kage.protocol.SetItemLockResponse;
import dev.kage.protocol.SortInventoryResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Kết quả các nút công cụ của túi đồ lô 29 — khoá vật phẩm (ADR-159) và sắp xếp túi (ADR-160). Nút nằm trong túi đồ
 * ({@link InventoryCoordinator}); lớp này chỉ nhận kết quả, in một dòng hệ thống và hỏi lại túi.
 */
final class BagActionsCoordinator implements WindowFlow {

    private static final String OUTCOME_PREFIX = "SET_ITEM_LOCK_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    BagActionsCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_SET_ITEM_LOCK_RESPONSE_VALUE) {
            chat.showSystemNotice(describe(SetItemLockResponse.parseFrom(frame.payload())));
            return true;
        }
        if (opcode == Opcode.OPCODE_SORT_INVENTORY_RESPONSE_VALUE) {
            chat.showSystemNotice(describe(SortInventoryResponse.parseFrom(frame.payload())));
            return true;
        }
        return false;
    }

    /** ADR-160 — dòng hệ thống sau khi sắp xếp túi. */
    String describe(SortInventoryResponse response) {
        if (!response.getOk()) {
            return KageI18n.textOrKey(i18n, "bag_sort.failed");
        }
        sender.listInventory();
        return response.getMergedRows() > 0
                ? KageI18n.formatOrKey(i18n, "bag_sort.merged", response.getMergedRows())
                : KageI18n.textOrKey(i18n, "bag_sort.ok");
    }

    /** Dòng hệ thống cho một phản hồi — tách ra để test không cần màn hình. */
    String describe(SetItemLockResponse response) {
        SetItemLockOutcome outcome = response.getOutcome();
        if (outcome == SetItemLockOutcome.SET_ITEM_LOCK_OUTCOME_OK) {
            sender.listInventory();
            return KageI18n.textOrKey(i18n, response.getLocked() ? "item_lock.locked" : "item_lock.unlocked");
        }
        String suffix = outcome == SetItemLockOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.textOrKey(i18n, "item_lock.outcome." + suffix);
    }
}
