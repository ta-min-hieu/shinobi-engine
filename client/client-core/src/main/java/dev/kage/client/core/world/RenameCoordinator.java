package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RenameCharacterCommand;
import dev.kage.protocol.RenameCharacterOutcome;
import dev.kage.protocol.RenameCharacterResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Dịch Danh Phù (ADR-148) — lệnh chat {@code /doiten <tên mới>} (hoặc {@code /rename}). Server kiểm luật tên, trùng
 * tên, phù trong túi và hồi chiêu 7 ngày; client chỉ gửi ý định và in kết quả thành dòng hệ thống.
 */
final class RenameCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/doiten";
    private static final String COMMAND_EN = "/rename";
    private static final String OUTCOME_PREFIX = "RENAME_CHARACTER_OUTCOME_";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    RenameCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!COMMAND_VI.equals(command) && !COMMAND_EN.equals(command)) {
            return false;
        }
        String newName = words.length > 1 ? words[1].strip() : "";
        if (newName.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "rename.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_RENAME_CHARACTER_COMMAND_VALUE,
                RenameCharacterCommand.newBuilder().setNewName(newName).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_RENAME_CHARACTER_RESPONSE_VALUE) {
            return false;
        }
        chat.showSystemNotice(describe(RenameCharacterResponse.parseFrom(frame.payload()), ZoneId.systemDefault()));
        return true;
    }

    /** Dòng hệ thống cho một phản hồi — tách ra để test không cần màn hình. */
    String describe(RenameCharacterResponse response, ZoneId zone) {
        RenameCharacterOutcome outcome = response.getOutcome();
        if (outcome == RenameCharacterOutcome.RENAME_CHARACTER_OUTCOME_OK) {
            sender.listInventory(); // vừa tiêu một Dịch Danh Phù
            return KageI18n.formatOrKey(i18n, "rename.ok", response.getName());
        }
        if (outcome == RenameCharacterOutcome.RENAME_CHARACTER_OUTCOME_ON_COOLDOWN
                && response.getNextAllowedAtEpochMs() > 0) {
            String when = WHEN.format(
                    Instant.ofEpochMilli(response.getNextAllowedAtEpochMs()).atZone(zone));
            return KageI18n.formatOrKey(i18n, "rename.outcome.on_cooldown", when);
        }
        String suffix = outcome == RenameCharacterOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.textOrKey(i18n, "rename.outcome." + suffix);
    }
}
