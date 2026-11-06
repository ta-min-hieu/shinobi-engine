package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.UseItemsCommand;
import dev.kage.protocol.UseItemsResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Dùng nhiều vật phẩm (ADR-176) — chọn một món trong túi rồi gõ {@code /dungnhieu [số]} (hoặc {@code /usemany};
 * không số = tối đa {@value #DEFAULT_COUNT}). Server lặp đúng đường dùng một món và dừng ở lần đầu bị từ chối; client
 * in kết quả và hỏi lại túi. Túi phúc mở nhiều bằng dòng "Mở 10" trong cửa sổ túi phúc.
 */
final class BulkUseCoordinator implements WindowFlow {

    static final int DEFAULT_COUNT = 50;

    private static final String COMMAND_VI = "/dungnhieu";
    private static final String COMMAND_EN = "/usemany";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private Supplier<Optional<Long>> selection = Optional::empty;

    BulkUseCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối món đang chọn trong túi đồ ({@code InventoryCoordinator}). */
    void useSelection(Supplier<Optional<Long>> selection) {
        this.selection = selection;
    }

    /** Số lần đọc từ đối số; rỗng ⇒ {@link #DEFAULT_COUNT}; không phải số dương ⇒ rỗng. Tĩnh để test. */
    static Optional<Integer> parseCount(String argument) {
        String trimmed = argument.strip();
        if (trimmed.isEmpty()) {
            return Optional.of(DEFAULT_COUNT);
        }
        if (!trimmed.matches("[0-9]{1,4}") || Integer.parseInt(trimmed) == 0) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(trimmed));
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        Optional<Integer> count = parseCount(words.length > 1 ? words[1] : "");
        Optional<Long> item = selection.get();
        if (count.isEmpty() || item.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "bulk_use.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_USE_ITEMS_COMMAND_VALUE,
                UseItemsCommand.newBuilder()
                        .setInventoryItemId(item.get())
                        .setCount(count.get())
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_USE_ITEMS_RESPONSE_VALUE) {
            return false;
        }
        UseItemsResponse response = UseItemsResponse.parseFrom(frame.payload());
        chat.showSystemNotice(describe(i18n, response));
        if (response.getUsed() > 0) {
            sender.listInventory();
        }
        return true;
    }

    /** Dòng hệ thống cho một phản hồi — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, UseItemsResponse response) {
        String reason =
                response.getStopErrorKey().isEmpty() ? "" : KageI18n.textOrKey(i18n, response.getStopErrorKey());
        if (response.getUsed() == 0) {
            return reason.isEmpty() ? KageI18n.textOrKey(i18n, "bulk_use.none") : reason;
        }
        String used = KageI18n.formatOrKey(i18n, "bulk_use.ok", response.getUsed(), response.getRemainingQuantity());
        return reason.isEmpty() ? used : used + " " + reason;
    }
}
