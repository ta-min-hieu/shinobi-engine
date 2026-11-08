package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.DestroyItemCommand;
import dev.kage.protocol.DestroyItemOutcome;
import dev.kage.protocol.DestroyItemResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Huỷ vật phẩm (ADR-231) — chọn một món trong túi, gõ {@code /huy} (hoặc {@code /destroy}) rồi gõ lại {@code /huy
 * xacnhan} trong 10 giây với CÙNG món đó. Hai bước để không lỡ tay.
 */
final class ItemDestroyCoordinator implements WindowFlow {

    static final long CONFIRM_WINDOW_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final String COMMAND_VI = "/huy";
    private static final String COMMAND_EN = "/destroy";
    private static final String OUTCOME_PREFIX = "DESTROY_ITEM_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final LongSupplier nanoClock;
    private final I18NBundle i18n = KageI18n.load();
    private Supplier<Optional<Long>> selection = Optional::empty;
    private OptionalLong pendingItem = OptionalLong.empty();
    private long pendingSince;

    ItemDestroyCoordinator(WorldCommandSender sender, ChatCoordinator chat, LongSupplier nanoClock) {
        this.sender = sender;
        this.chat = chat;
        this.nanoClock = nanoClock;
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối món đang chọn trong túi đồ. */
    void useSelection(Supplier<Optional<Long>> selection) {
        this.selection = selection;
    }

    /** Đối số là lời xác nhận ({@code xacnhan}/{@code confirm}). Tĩnh để test. */
    static boolean isConfirm(String argument) {
        String word = argument.strip().toLowerCase(Locale.ROOT);
        return "xacnhan".equals(word) || "confirm".equals(word);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        Optional<Long> item = selection.get();
        if (item.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "destroy.usage"));
            return true;
        }
        long now = nanoClock.getAsLong();
        boolean confirmed = words.length > 1 && isConfirm(words[1]);
        if (confirmed
                && pendingItem.isPresent()
                && pendingItem.getAsLong() == item.get()
                && now - pendingSince <= CONFIRM_WINDOW_NANOS) {
            pendingItem = OptionalLong.empty();
            sender.sendReadRequest(
                    Opcode.OPCODE_DESTROY_ITEM_COMMAND_VALUE,
                    DestroyItemCommand.newBuilder().setItemId(item.get()).build());
            return true;
        }
        pendingItem = OptionalLong.of(item.get());
        pendingSince = now;
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "destroy.confirm"));
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_DESTROY_ITEM_RESPONSE_VALUE) {
            return false;
        }
        DestroyItemOutcome outcome =
                DestroyItemResponse.parseFrom(frame.payload()).getOutcome();
        String key = outcome == DestroyItemOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "destroy." + key));
        if (outcome == DestroyItemOutcome.DESTROY_ITEM_OUTCOME_OK) {
            sender.listInventory();
        }
        return true;
    }
}
