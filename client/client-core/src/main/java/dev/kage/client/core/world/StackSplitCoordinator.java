package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SplitStackCommand;
import dev.kage.protocol.SplitStackOutcome;
import dev.kage.protocol.SplitStackResponse;
import dev.kage.protocol.wire.PacketCodec;

/** Tách chồng vật phẩm (ADR-215) — chọn một chồng trong túi rồi gõ {@code /tach <số>} (hoặc {@code /split}). */
final class StackSplitCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/tach";
    private static final String COMMAND_EN = "/split";
    private static final String OUTCOME_PREFIX = "SPLIT_STACK_OUTCOME_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private Supplier<Optional<Long>> selection = Optional::empty;

    StackSplitCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối món đang chọn trong túi đồ ({@code InventoryCoordinator}). */
    void useSelection(Supplier<Optional<Long>> selection) {
        this.selection = selection;
    }

    /** Số cái tách từ đối số: số dương tối đa 4 chữ số; khác ⇒ rỗng. Tĩnh để test. */
    static Optional<Integer> parseAmount(String argument) {
        String trimmed = argument.strip();
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
        Optional<Integer> amount = parseAmount(words.length > 1 ? words[1] : "");
        Optional<Long> item = selection.get();
        if (amount.isEmpty() || item.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "split.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SPLIT_STACK_COMMAND_VALUE,
                SplitStackCommand.newBuilder()
                        .setInventoryItemId(item.get())
                        .setAmount(amount.get())
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SPLIT_STACK_RESPONSE_VALUE) {
            return false;
        }
        SplitStackOutcome outcome =
                SplitStackResponse.parseFrom(frame.payload()).getOutcome();
        String key = outcome == SplitStackOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "split." + key));
        if (outcome == SplitStackOutcome.SPLIT_STACK_OUTCOME_OK) {
            sender.listInventory();
        }
        return true;
    }
}
