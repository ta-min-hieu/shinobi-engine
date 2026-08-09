package dev.kage.client.core.world;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.client.core.net.GameConnection;
import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.JumpCommand;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.UseSkillCommand;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Gửi Ý ĐỊNH của người chơi lên server (CLIENT.md §1 — client không bao giờ gửi KẾT QUẢ). Gom cả
 * năm lệnh gameplay về một chỗ vì chúng chia sẻ đúng một quyết định đáng nhớ: <b>mất kết nối tạm
 * thời KHÔNG phải lỗi</b>.
 *
 * <p>{@link GameConnection} tự kết nối lại; ý định mới nhất sẽ được gửi ở lần người chơi thao tác
 * tiếp theo, nên retry thủ công ở đây chỉ tạo ra hàng đợi lệnh cũ mèm đổ ập lên server ngay khi nối
 * lại. Vì vậy mọi hàm ở đây nuốt {@link IOException} xuống mức {@code debug} — đây là ngoại lệ CÓ
 * CHỦ ĐÍCH của "cấm nuốt exception" (/CLAUDE.md §11), không phải quên xử lý.
 */
final class WorldCommandSender {

    private static final Logger LOG = LoggerFactory.getLogger(WorldCommandSender.class);

    private final GameConnection connection;

    WorldCommandSender(GameConnection connection) {
        this.connection = connection;
    }

    /**
     * @param dirX hướng đi ngang đã chuẩn hoá (-1/0/1). {@code dirY} luôn 0 kể từ ADR-033 — field
     *     còn trên dây nhưng server-world bỏ qua, xem {@code WorldScreen}.
     */
    void moveDirection(float dirX) {
        MoveCommand command = MoveCommand.newBuilder().setDirX(dirX).setDirY(0f).build();
        send(Opcode.OPCODE_MOVE_COMMAND_VALUE, command.toByteArray(), "MoveCommand");
    }

    void jump() {
        send(Opcode.OPCODE_JUMP_COMMAND_VALUE, JumpCommand.newBuilder().build().toByteArray(), "JumpCommand");
    }

    /** Đánh thường một đòn — mục tiêu lấy từ phía server, gói này rỗng (combat.proto). */
    void attack() {
        send(
                Opcode.OPCODE_ATTACK_COMMAND_VALUE,
                AttackCommand.newBuilder().build().toByteArray(),
                "AttackCommand");
    }

    void setTarget(long targetEntityId) {
        SetTargetCommand command =
                SetTargetCommand.newBuilder().setTargetEntityId(targetEntityId).build();
        send(Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, command.toByteArray(), "SetTargetCommand");
    }

    void useSkill(String skillCode, long targetEntityId) {
        UseSkillCommand command = UseSkillCommand.newBuilder()
                .setSkillCode(skillCode)
                .setTargetEntityId(targetEntityId)
                .build();
        send(Opcode.OPCODE_USE_SKILL_COMMAND_VALUE, command.toByteArray(), "UseSkillCommand");
    }

    /** Gói tin đến từ server — {@code null} khi hàng đợi rỗng (xem {@link GameConnection}). */
    PacketCodec.Frame pollInbound() {
        return connection.pollInbound();
    }

    private void send(int opcode, byte[] payload, String commandName) {
        try {
            connection.send(opcode, connection.nextSequenceId(), payload);
        } catch (IOException e) {
            LOG.debug("Gửi {} thất bại (mất kết nối tạm thời)", commandName, e);
        }
    }
}
