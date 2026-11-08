package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BulkDismantleCommand;
import dev.kage.protocol.BulkDismantleResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Phân giải hàng loạt (ADR-232) — {@code /phangiai <cấp>} (hoặc {@code /dismantleall}) phân giải mọi trang bị KHÔNG
 * khoá, KHÔNG đang mặc, KHÔNG khảm ngọc có cấp yêu cầu ≤ số đó.
 */
final class BulkDismantleCoordinator implements WindowFlow {

    static final int MAX_LEVEL = 100;
    private static final String COMMAND_VI = "/phangiai";
    private static final String COMMAND_EN = "/dismantleall";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    BulkDismantleCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Cấp ngưỡng: 1…100; khác ⇒ rỗng. Tĩnh để test. */
    static Optional<Integer> parseLevel(String argument) {
        String trimmed = argument.strip();
        if (!trimmed.matches("[0-9]{1,3}")) {
            return Optional.empty();
        }
        int level = Integer.parseInt(trimmed);
        return level >= 1 && level <= MAX_LEVEL ? Optional.of(level) : Optional.empty();
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        Optional<Integer> level = parseLevel(words.length > 1 ? words[1] : "");
        if (level.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "dismantleall.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_BULK_DISMANTLE_COMMAND_VALUE,
                BulkDismantleCommand.newBuilder()
                        .setMaxRequiredLevel(level.get())
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_BULK_DISMANTLE_RESPONSE_VALUE) {
            return false;
        }
        BulkDismantleResponse response = BulkDismantleResponse.parseFrom(frame.payload());
        if (response.getDismantled() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "dismantleall.none"));
            return true;
        }
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n, "dismantleall.done", response.getDismantled(), response.getGold(), response.getHerbs()));
        sender.listInventory();
        return true;
    }
}
