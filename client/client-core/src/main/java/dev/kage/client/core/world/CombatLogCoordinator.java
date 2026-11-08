package dev.kage.client.core.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SkillResultEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nhật ký chiến đấu (ADR-226) — client thuần: nghe (không "nuốt") mọi kết quả đòn đánh / kỹ năng mà server đã gửi, giữ
 * {@value #MAX_LINES} dòng gần nhất có dính tới chính mình; {@code /nhatkychien} (hoặc {@code /combatlog}) in ra khung
 * chat. Không gói mới: chỉ ghi lại những gì client vốn nhận.
 */
final class CombatLogCoordinator implements WindowFlow {

    static final int MAX_LINES = 20;

    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final Deque<String> lines = new ArrayDeque<>();
    private final Map<Long, String> monsterCodes = new HashMap<>();
    private long localCharacterId;

    CombatLogCoordinator(ChatCoordinator chat) {
        this.chat = chat;
        chat.addSlashCommands(this::onSlash);
    }

    /** Id nhân vật của chính mình (một lần, lúc dựng màn hình). */
    void useLocalId(long characterId) {
        this.localCharacterId = characterId;
    }

    private boolean onSlash(String line) {
        String command = line.strip().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (!"/nhatkychien".equals(command) && !"/combatlog".equals(command)) {
            return false;
        }
        if (lines.isEmpty()) {
            chat.showSystemNotice(KageI18n.textOrKey(i18n, "combat_log.empty"));
            return true;
        }
        chat.showSystemNotice(KageI18n.textOrKey(i18n, "combat_log.title"));
        lines.forEach(chat::showSystemNotice);
        return true;
    }

    /** Chỉ NGHE — luôn trả {@code false} để màn hình vẫn xử lý các gói này như cũ. */
    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE) {
            MonsterSpawnedEvent spawned = MonsterSpawnedEvent.parseFrom(frame.payload());
            monsterCodes.put(spawned.getMonsterId(), spawned.getMonsterCode());
        } else if (opcode == Opcode.OPCODE_MONSTER_DESPAWNED_EVENT_VALUE) {
            monsterCodes.remove(MonsterDespawnedEvent.parseFrom(frame.payload()).getMonsterId());
        } else if (opcode == Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE) {
            AttackResultEvent hit = AttackResultEvent.parseFrom(frame.payload());
            record(new Hit(hit.getAttackerEntityId(), hit.getTargetEntityId(), hit.getDamage(), ""), hit.getIsMiss());
        } else if (opcode == Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE) {
            SkillResultEvent hit = SkillResultEvent.parseFrom(frame.payload());
            record(
                    new Hit(hit.getAttackerEntityId(), hit.getTargetEntityId(), hit.getDamage(), hit.getSkillCode()),
                    hit.getIsMiss());
        }
        return false;
    }

    /** Một đòn: ai đánh ai, bao nhiêu, bằng chiêu gì ("" = đánh thường). */
    record Hit(long attackerId, long targetId, int damage, String skillCode) {}

    private void record(Hit hit, boolean miss) {
        boolean mine = hit.attackerId() == localCharacterId;
        if (!mine && hit.targetId() != localCharacterId) {
            return; // chỉ đòn có dính tới chính mình
        }
        String other = nameOf(mine ? hit.targetId() : hit.attackerId());
        String how = hit.skillCode().isEmpty()
                ? KageI18n.textOrKey(i18n, "combat_log.basic_attack")
                : KageI18n.textOrKey(i18n, "skill.name." + hit.skillCode());
        String key = (mine ? "combat_log.dealt" : "combat_log.taken") + (miss ? "_miss" : "");
        if (lines.size() >= MAX_LINES) {
            lines.removeFirst();
        }
        lines.addLast(KageI18n.formatOrKey(i18n, key, other, hit.damage(), how));
    }

    private String nameOf(long entityId) {
        String code = monsterCodes.get(entityId);
        return code == null
                ? KageI18n.formatOrKey(i18n, "combat_log.someone", entityId)
                : KageI18n.textOrKey(i18n, "hud.monster." + code);
    }

    @Override
    public void reset() {
        lines.clear();
        monsterCodes.clear();
    }

    /** Số dòng đang giữ (test). */
    int size() {
        return lines.size();
    }
}
