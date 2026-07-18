// TEMPLATE — Packet definitions. record + sealed. Client gửi Ý ĐỊNH, server trả KẾT QUẢ.
// Thực tế: payload sinh từ .proto; các record này là wrapper Java sau decode.
package dev.kage.protocol.combat;

import dev.kage.protocol.Command;
import dev.kage.protocol.Event;
import dev.kage.protocol.Response;

/**
 * Nhóm packet combat. Opcode thuộc dải 400–499 ([NETWORK §4]).
 *
 * <p>Command = client→server (cần Response). Event = server→client (không cần trả lời),
 * tên ở QUÁ KHỨ. Chỉ được THÊM field mới, không đổi/xoá field cũ ([NETWORK §6]).
 */
public final class CombatPackets {

    private CombatPackets() {}

    /** Client gửi Ý ĐỊNH dùng skill — KHÔNG gửi damage (server tính, Điều 15). */
    public record UseSkillCommand(int skillId, long targetEntityId) implements Command {
        public UseSkillCommand {
            if (skillId <= 0) throw new IllegalArgumentException("skillId phải > 0: " + skillId);
        }
    }

    /** Server trả kết quả — khớp Command qua sequenceId ở header ([NETWORK §5]). */
    public record UseSkillResponse(boolean accepted, int errorCode) implements Response {}

    /** Sự kiện ĐÃ xảy ra (quá khứ). Fan-out cho quest/loot/ranking qua Event Bus (ADR-008). */
    public record MonsterKilledEvent(long monsterEntityId, String monsterCode, long killerCharacterId)
            implements Event {}
}
