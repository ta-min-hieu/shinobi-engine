package dev.kage.client.core.world;

import java.util.Locale;
import java.util.Map;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.Emote;
import dev.kage.protocol.EmoteEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendEmoteCommand;
import dev.kage.protocol.SendEmoteOutcome;
import dev.kage.protocol.SendEmoteResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Biểu cảm (ADR-092) — gõ {@code /chao}, {@code /cui}... trong ô chat; server phát {@code EmoteEvent} cho mọi người
 * đang nhìn thấy mình (kèm chính mình) và client vẽ chữ nổi trên đầu nhân vật đó. Không có cửa sổ.
 */
final class EmoteCoordinator implements WindowFlow {

    /** Vẽ một dòng chữ trên đầu một nhân vật (chính mình hoặc người khác đang thấy) — {@code WorldScreen} nối. */
    @FunctionalInterface
    interface EmoteDisplay {
        void show(long characterId, String text);

        /** ADR-325 — số người chơi khác đang thấy (mặc định 0 cho bản hiển thị không biết). */
        default int visibleOthers() {
            return 0;
        }
    }

    /** Lệnh gõ → biểu cảm. Không dấu để gõ được trên mọi bộ gõ. */
    static final Map<String, Emote> COMMANDS = Map.of(
            "/chao", Emote.EMOTE_GREET,
            "/cui", Emote.EMOTE_BOW,
            "/cuoi", Emote.EMOTE_LAUGH,
            "/khoc", Emote.EMOTE_CRY,
            "/gian", Emote.EMOTE_ANGRY,
            "/vui", Emote.EMOTE_CHEER);

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final EmoteDisplay display;
    private final I18NBundle i18n = KageI18n.load();

    EmoteCoordinator(WorldCommandSender sender, ChatCoordinator chat, EmoteDisplay display) {
        this.sender = sender;
        this.chat = chat;
        this.display = display;
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        Emote emote = COMMANDS.get(line.toLowerCase(Locale.ROOT));
        if (emote == null) {
            return false;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SEND_EMOTE_COMMAND_VALUE,
                SendEmoteCommand.newBuilder().setEmote(emote).build());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_EMOTE_EVENT_VALUE) {
            EmoteEvent event = EmoteEvent.parseFrom(frame.payload());
            display.show(event.getCharacterId(), text(labelKey(event.getEmote())));
            return true;
        }
        if (opcode == Opcode.OPCODE_SEND_EMOTE_RESPONSE_VALUE) {
            SendEmoteOutcome outcome =
                    SendEmoteResponse.parseFrom(frame.payload()).getOutcome();
            if (outcome == SendEmoteOutcome.SEND_EMOTE_OUTCOME_RATE_LIMITED) {
                chat.showSystemNotice(text("emote.error.rate_limited"));
            } else if (outcome != SendEmoteOutcome.SEND_EMOTE_OUTCOME_OK) {
                chat.showSystemNotice(text("emote.error.failed"));
            }
            return true;
        }
        return false;
    }

    /** {@code EMOTE_GREET} → {@code emote.greet}. */
    static String labelKey(Emote emote) {
        String name = emote.name();
        return name.startsWith("EMOTE_")
                ? "emote." + name.substring("EMOTE_".length()).toLowerCase(Locale.ROOT)
                : "emote.unknown";
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }
}
