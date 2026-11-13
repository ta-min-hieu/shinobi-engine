package dev.kage.client.core.world;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BreakthroughEntry;
import dev.kage.protocol.GetBreakthroughHistoryRequest;
import dev.kage.protocol.GetBreakthroughHistoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/** Lịch sử đột phá cảnh giới (ADR-322) — {@code /dotpha} (hoặc {@code /breakthroughs}): ngày đạt từng cảnh giới. */
final class BreakthroughHistoryCoordinator implements WindowFlow {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();

    BreakthroughHistoryCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        String command = line.strip().toLowerCase(Locale.ROOT);
        if (!"/dotpha".equals(command) && !"/breakthroughs".equals(command)) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_GET_BREAKTHROUGH_HISTORY_REQUEST_VALUE,
                GetBreakthroughHistoryRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_BREAKTHROUGH_HISTORY_RESPONSE_VALUE) {
            return false;
        }
        GetBreakthroughHistoryResponse response = GetBreakthroughHistoryResponse.parseFrom(frame.payload());
        if (response.getEntriesCount() == 0) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "breakthroughs.empty"));
            return true;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "breakthroughs.header"));
        ZoneId zone = ZoneId.systemDefault();
        for (BreakthroughEntry entry : response.getEntriesList()) {
            chat.showSystemNotice(KageI18n.formatOrKey(
                    i18n,
                    "breakthroughs.line",
                    DATE.format(Instant.ofEpochSecond(entry.getAtEpochSeconds()).atZone(zone)),
                    KageI18n.textOrKey(i18n, realmKey(entry.getRealm())),
                    entry.getLevel()));
        }
        return true;
    }

    /** Khoá i18n tên cảnh giới (ngoài khoảng ⇒ cảnh giới gần nhất). Tĩnh để test. */
    static String realmKey(int realm) {
        return OnlineCountCoordinator.REALM_KEYS.get(
                Math.clamp(realm, 0, OnlineCountCoordinator.REALM_KEYS.size() - 1));
    }
}
