package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.ContactPrivacySetting;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetContactPrivacyCommand;
import dev.kage.protocol.SetContactPrivacyResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Riêng tư liên lạc — {@code /khonglamphien bat|tat} ({@code /dnd on|off}, ADR-236) và {@code /chanloimoi bat|tat}
 * ({@code /blockinvites on|off}, ADR-237); không kèm gì = hỏi trạng thái. Bạn bè luôn liên lạc được.
 */
final class ContactPrivacyCoordinator implements WindowFlow {

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    ContactPrivacyCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Công tắc ứng với lệnh; lệnh khác ⇒ rỗng. Tĩnh để test. */
    static Optional<ContactPrivacySetting> settingOf(String command) {
        return switch (command.toLowerCase(Locale.ROOT)) {
            case "/khonglamphien", "/dnd" -> Optional.of(ContactPrivacySetting.CONTACT_PRIVACY_SETTING_DO_NOT_DISTURB);
            case "/chanloimoi", "/blockinvites" ->
                Optional.of(ContactPrivacySetting.CONTACT_PRIVACY_SETTING_BLOCK_STRANGER_INVITES);
            default -> Optional.empty();
        };
    }

    /** {@code bat/on} ⇒ true, {@code tat/off} ⇒ false, khác ⇒ rỗng. Tĩnh để test. */
    static Optional<Boolean> parseSwitch(String argument) {
        return switch (argument.strip().toLowerCase(Locale.ROOT)) {
            case "bat", "on" -> Optional.of(Boolean.TRUE);
            case "tat", "off" -> Optional.of(Boolean.FALSE);
            default -> Optional.empty();
        };
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        Optional<ContactPrivacySetting> setting = settingOf(words[0]);
        if (setting.isEmpty()) {
            return false;
        }
        SetContactPrivacyCommand.Builder request = SetContactPrivacyCommand.newBuilder();
        if (words.length > 1) {
            Optional<Boolean> enabled = parseSwitch(words[1]);
            if (enabled.isEmpty()) {
                chat.showSystemNotice(KageI18n.textOrKey(i18n, "privacy.usage"));
                return true;
            }
            request.setSetting(setting.get()).setEnabled(enabled.get());
        }
        sender.sendReadRequest(Opcode.OPCODE_SET_CONTACT_PRIVACY_COMMAND_VALUE, request.build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_SET_CONTACT_PRIVACY_RESPONSE_VALUE) {
            return false;
        }
        SetContactPrivacyResponse response = SetContactPrivacyResponse.parseFrom(frame.payload());
        chat.showSystemNotice(KageI18n.formatOrKey(
                i18n,
                "privacy.status",
                KageI18n.textOrKey(i18n, response.getDoNotDisturb() ? "privacy.on" : "privacy.off"),
                KageI18n.textOrKey(i18n, response.getBlockStrangerInvites() ? "privacy.on" : "privacy.off")));
        return true;
    }
}
