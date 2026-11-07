package dev.kage.tools.loadtest.probe;

import java.util.Optional;

import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.Opcode;

/**
 * Dòng log probe cho gói tung xúc xắc chia đồ nhóm (ADR-150) và tóm tắt khi chết (ADR-195) — tách khỏi {@link
 * ProbeEventLine} vì lớp đó đã chạm trần
 * CyclomaticComplexity cả lớp.
 */
final class LootRollEventLine {

    private LootRollEventLine() {}

    static Optional<String> describe(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        return switch (opcode) {
            case Opcode.OPCODE_LOOT_ROLL_STARTED_EVENT_VALUE -> { // ADR-150
                dev.kage.protocol.LootRollStartedEvent r = dev.kage.protocol.LootRollStartedEvent.parseFrom(payload);
                yield Optional.of("rollId=" + r.getRollId() + " tpl" + r.getItemTemplateId() + "x" + r.getQuantity()
                        + " seconds=" + r.getSeconds());
            }
            case Opcode.OPCODE_LOOT_ROLL_RESULT_EVENT_VALUE -> { // ADR-150
                dev.kage.protocol.LootRollResultEvent r = dev.kage.protocol.LootRollResultEvent.parseFrom(payload);
                yield Optional.of("rollId=" + r.getRollId() + " winner=" + r.getWinnerName() + " byMail="
                        + r.getByMail() + " entries="
                        + r.getEntriesList().stream()
                                .map(e -> e.getName() + ":" + e.getChoice() + ":" + e.getRoll())
                                .toList());
            }
            case Opcode.OPCODE_DEATH_RECAP_EVENT_VALUE -> { // ADR-195
                dev.kage.protocol.DeathRecapEvent r = dev.kage.protocol.DeathRecapEvent.parseFrom(payload);
                yield Optional.of("killer=" + r.getKillerEntityId() + " window=" + r.getWindowSeconds() + "s entries="
                        + r.getEntriesList().stream()
                                .map(e -> e.getAttackerEntityId() + "/" + e.getAttackerName() + ":" + e.getDamage()
                                        + "dmg/" + e.getHits() + "hits/" + e.getTopSkillCode())
                                .toList());
            }
            default -> Optional.empty();
        };
    }
}
