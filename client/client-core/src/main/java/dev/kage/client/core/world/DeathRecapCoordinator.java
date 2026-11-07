package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.DeathRecapEntry;
import dev.kage.protocol.DeathRecapEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Tóm tắt khi chết (ADR-195) — server gửi ai gây bao nhiêu sát thương trong 10 giây cuối; client in vào khung chat.
 * Người chơi có tên từ server; quái chỉ có id âm nên lớp này tự nhớ mã quái từ gói spawn (KHÔNG nuốt gói đó).
 */
final class DeathRecapCoordinator implements WindowFlow {

    /** Trần mã quái nhớ được — vượt thì quên hết rồi nhớ lại từ gói spawn kế (không phình theo thời gian chơi). */
    private static final int MAX_REMEMBERED_MONSTERS = 512;

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Map<Long, String> monsterCodes = new HashMap<>();

    DeathRecapCoordinator(ChatCoordinator chat) {
        this.chat = chat;
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE) {
            MonsterSpawnedEvent spawned = MonsterSpawnedEvent.parseFrom(frame.payload());
            if (monsterCodes.size() >= MAX_REMEMBERED_MONSTERS) {
                monsterCodes.clear();
            }
            monsterCodes.put(spawned.getMonsterId(), spawned.getMonsterCode());
            return false; // KHÔNG nuốt — màn hình thế giới vẽ quái theo gói này
        }
        if (opcode == Opcode.OPCODE_DEATH_RECAP_EVENT_VALUE) {
            describe(i18n, DeathRecapEvent.parseFrom(frame.payload()), monsterCodes)
                    .forEach(chat::showSystemNotice);
            return true;
        }
        return false;
    }

    /** Các dòng hệ thống — tĩnh để test không cần màn hình. */
    static List<String> describe(I18NBundle i18n, DeathRecapEvent event, Map<Long, String> monsterCodes) {
        List<String> lines = new ArrayList<>();
        lines.add(KageI18n.formatOrKey(i18n, "death_recap.header", event.getWindowSeconds()));
        for (DeathRecapEntry entry : event.getEntriesList()) {
            String skill = entry.getTopSkillCode().isEmpty()
                    ? KageI18n.textOrKey(i18n, "death_recap.basic_attack")
                    : KageI18n.textOrKey(i18n, "hud.skill." + entry.getTopSkillCode());
            lines.add(KageI18n.formatOrKey(
                    i18n,
                    entry.getAttackerEntityId() == event.getKillerEntityId()
                            ? "death_recap.row_killer"
                            : "death_recap.row",
                    attackerName(i18n, entry, monsterCodes),
                    entry.getDamage(),
                    entry.getHits(),
                    skill));
        }
        return lines;
    }

    private static String attackerName(I18NBundle i18n, DeathRecapEntry entry, Map<Long, String> monsterCodes) {
        if (!entry.getAttackerName().isEmpty()) {
            return ChatController.sanitize(entry.getAttackerName());
        }
        String code = monsterCodes.get(entry.getAttackerEntityId());
        return code == null
                ? KageI18n.textOrKey(i18n, "death_recap.unknown_monster")
                : KageI18n.textOrKey(i18n, "hud.monster." + code);
    }

    @Override
    public void reset() {
        monsterCodes.clear();
    }
}
