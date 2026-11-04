package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RenameGuildCommand;
import dev.kage.protocol.RenameGuildOutcome;
import dev.kage.protocol.RenameGuildResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Đổi tên bang hội (ADR-172) — lệnh chat {@code /doitenbang <tên mới>} (hoặc {@code /guildrename}). Server kiểm bang
 * chủ, luật tên, tên trùng, hồi chiêu 30 ngày và vàng; client chỉ gửi ý định và in kết quả.
 */
final class GuildRenameCoordinator implements WindowFlow {

    private static final String COMMAND_VI = "/doitenbang";
    private static final String COMMAND_EN = "/guildrename";
    private static final String OUTCOME_PREFIX = "RENAME_GUILD_OUTCOME_";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    GuildRenameCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
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
        if (words.length < 2 || words[1].isBlank()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "guild_rename.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_RENAME_GUILD_COMMAND_VALUE,
                RenameGuildCommand.newBuilder().setNewName(words[1].strip()).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_RENAME_GUILD_RESPONSE_VALUE) {
            return false;
        }
        chat.showSystemNotice(describe(i18n, RenameGuildResponse.parseFrom(frame.payload()), ZoneId.systemDefault()));
        return true;
    }

    /** Dòng hệ thống cho một phản hồi — tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, RenameGuildResponse response, ZoneId zone) {
        RenameGuildOutcome outcome = response.getOutcome();
        if (outcome == RenameGuildOutcome.RENAME_GUILD_OUTCOME_OK) {
            return KageI18n.formatOrKey(i18n, "guild_rename.ok", response.getName(), response.getFee());
        }
        if (outcome == RenameGuildOutcome.RENAME_GUILD_OUTCOME_ON_COOLDOWN) {
            String when = WHEN.format(
                    Instant.ofEpochMilli(response.getNextAllowedAtEpochMs()).atZone(zone));
            return KageI18n.formatOrKey(i18n, "guild_rename.outcome.on_cooldown", when);
        }
        String suffix = outcome == RenameGuildOutcome.UNRECOGNIZED
                ? "unspecified"
                : outcome.name().substring(OUTCOME_PREFIX.length()).toLowerCase(Locale.ROOT);
        return KageI18n.textOrKey(i18n, "guild_rename.outcome." + suffix);
    }
}
