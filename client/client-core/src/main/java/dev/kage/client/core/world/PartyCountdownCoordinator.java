package dev.kage.client.core.world;

import java.util.Locale;
import java.util.OptionalInt;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.StartPartyCountdownCommand;
import dev.kage.protocol.StartPartyCountdownResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Đếm ngược nhóm (ADR-264) — {@code /demnguoc [3-10]} (hoặc {@code /countdown}), mặc định {@value #DEFAULT_SECONDS}
 * giây. Server phát từng nhịp vào chat của cả nhóm; lớp này chỉ gửi ý định và báo lý do bị từ chối (server vẫn kiểm
 * khoảng 3–10).
 */
final class PartyCountdownCoordinator implements WindowFlow {

    static final int DEFAULT_SECONDS = 5;

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    PartyCountdownCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Số giây từ đối số (rỗng ⇒ mặc định); rỗng khi không phải số. Tĩnh để test. */
    static OptionalInt seconds(String argument) {
        String trimmed = argument.strip();
        if (trimmed.isEmpty()) {
            return OptionalInt.of(DEFAULT_SECONDS);
        }
        return trimmed.matches("[0-9]{1,3}") ? OptionalInt.of(Integer.parseInt(trimmed)) : OptionalInt.empty();
    }

    private boolean onSlash(String line) {
        String[] parts = line.strip().split("\\s+", 2);
        String command = parts[0].toLowerCase(Locale.ROOT);
        if (!"/demnguoc".equals(command) && !"/countdown".equals(command)) {
            return false;
        }
        OptionalInt seconds = seconds(parts.length > 1 ? parts[1] : "");
        if (seconds.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "countdown.usage"));
            return true;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_START_PARTY_COUNTDOWN_COMMAND_VALUE,
                StartPartyCountdownCommand.newBuilder()
                        .setSeconds(seconds.getAsInt())
                        .build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_START_PARTY_COUNTDOWN_RESPONSE_VALUE) {
            return false;
        }
        String key =
                switch (StartPartyCountdownResponse.parseFrom(frame.payload()).getOutcome()) {
                    case START_PARTY_COUNTDOWN_OUTCOME_OK -> "";
                    case START_PARTY_COUNTDOWN_OUTCOME_INVALID_SECONDS -> "countdown.usage";
                    case START_PARTY_COUNTDOWN_OUTCOME_ALREADY_ACTIVE -> "countdown.already_active";
                    default -> "countdown.not_leader";
                };
        if (!key.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, key));
        }
        return true;
    }
}
