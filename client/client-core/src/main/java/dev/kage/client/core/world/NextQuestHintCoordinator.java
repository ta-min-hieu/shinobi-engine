package dev.kage.client.core.world;

import java.util.Locale;
import java.util.function.Consumer;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.GetNextQuestHintRequest;
import dev.kage.protocol.GetNextQuestHintResponse;
import dev.kage.protocol.NextQuestHintKind;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Gợi ý nhiệm vụ tiếp (ADR-228) — {@code /tieptheo} (hoặc {@code /next}) cho biết nên làm nhiệm vụ nào / gặp NPC nào;
 * {@code /tieptheo di} ({@code /next go}) còn tự đi tới NPC đó (tự tìm đường ADR-206).
 */
final class NextQuestHintCoordinator implements WindowFlow {

    private static final String KIND_PREFIX = "NEXT_QUEST_HINT_KIND_";

    private final WorldCommandSender sender;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private Consumer<String> walker = npcCode -> {};
    private boolean walkAfterReply;

    NextQuestHintCoordinator(WorldCommandSender sender, ChatCoordinator chat) {
        this.sender = sender;
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Nối tự tìm đường tới NPC theo mã (một lần, lúc dựng màn hình). */
    void useWalker(Consumer<String> walkToNpc) {
        this.walker = walkToNpc;
    }

    private boolean onSlash(String line) {
        String[] words = line.strip().split("\\s+", 2);
        String command = words[0].toLowerCase(Locale.ROOT);
        if (!"/tieptheo".equals(command) && !"/next".equals(command)) {
            return false;
        }
        String arg = words.length > 1 ? words[1].strip().toLowerCase(Locale.ROOT) : "";
        walkAfterReply = "di".equals(arg) || "go".equals(arg);
        sender.sendReadRequest(
                Opcode.OPCODE_GET_NEXT_QUEST_HINT_REQUEST_VALUE, GetNextQuestHintRequest.getDefaultInstance());
        return true;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        if (frame.header().opcode() != Opcode.OPCODE_GET_NEXT_QUEST_HINT_RESPONSE_VALUE) {
            return false;
        }
        GetNextQuestHintResponse hint = GetNextQuestHintResponse.parseFrom(frame.payload());
        chat.showSystemNotice(describe(i18n, hint));
        if (walkAfterReply && !hint.getNpcCode().isEmpty()) {
            walker.accept(hint.getNpcCode());
        }
        walkAfterReply = false;
        return true;
    }

    /** Một dòng gợi ý. Tĩnh để test không cần màn hình. */
    static String describe(I18NBundle i18n, GetNextQuestHintResponse hint) {
        String kind = hint.getKind() == NextQuestHintKind.UNRECOGNIZED
                ? "none"
                : hint.getKind().name().substring(KIND_PREFIX.length()).toLowerCase(Locale.ROOT);
        String quest = KageI18n.textOrKey(i18n, "quest.name." + hint.getQuestCode());
        String npc = hint.getNpcCode().isEmpty() ? "" : KageI18n.textOrKey(i18n, "npc." + hint.getNpcCode() + ".name");
        String key = "next_quest." + kind + (npc.isEmpty() ? "" : "_npc");
        return KageI18n.formatOrKey(i18n, key, quest, npc);
    }
}
