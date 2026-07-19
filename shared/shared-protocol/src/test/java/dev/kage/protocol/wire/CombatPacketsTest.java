package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetTargetCommand;
import org.junit.jupiter.api.Test;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code
 * PacketCodec} + protobuf thật cho toàn bộ packet Combat/Skill (400-499, NETWORK.md §4).
 */
class CombatPacketsTest {

    @Test
    void shouldRoundTripSetTargetCommand() throws InvalidProtocolBufferException {
        var command = SetTargetCommand.newBuilder().setTargetEntityId(-1L).build();
        var header = new PacketHeader(Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, ProtocolVersion.CURRENT, 5);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        var decoded = PacketCodec.decodeFrame(frameBytes);
        var parsed = SetTargetCommand.parseFrom(decoded.payload());

        assertThat(decoded.header()).isEqualTo(header);
        assertThat(parsed.getTargetEntityId()).isEqualTo(-1L);
    }

    @Test
    void shouldRoundTripAttackResultEvent() throws InvalidProtocolBufferException {
        var event = AttackResultEvent.newBuilder()
                .setAttackerEntityId(1L)
                .setTargetEntityId(-1L)
                .setDamage(12)
                .setIsCrit(true)
                .setIsMiss(false)
                .setTargetCurrentHp(38)
                .setTargetDied(false)
                .build();
        var header = new PacketHeader(Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        var parsed = AttackResultEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getAttackerEntityId()).isEqualTo(1L);
        assertThat(parsed.getTargetEntityId()).isEqualTo(-1L);
        assertThat(parsed.getDamage()).isEqualTo(12);
        assertThat(parsed.getIsCrit()).isTrue();
        assertThat(parsed.getIsMiss()).isFalse();
        assertThat(parsed.getTargetCurrentHp()).isEqualTo(38);
        assertThat(parsed.getTargetDied()).isFalse();
    }

    @Test
    void shouldRoundTripMonsterSpawnedEvent() throws InvalidProtocolBufferException {
        var event = MonsterSpawnedEvent.newBuilder()
                .setMonsterId(-1L)
                .setMonsterCode("training_dummy")
                .setX(5f)
                .setY(2f)
                .setCurrentHp(50)
                .setMaxHp(50)
                .build();
        var header = new PacketHeader(Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        var parsed = MonsterSpawnedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getMonsterId()).isEqualTo(-1L);
        assertThat(parsed.getMonsterCode()).isEqualTo("training_dummy");
        assertThat(parsed.getX()).isEqualTo(5f);
        assertThat(parsed.getY()).isEqualTo(2f);
        assertThat(parsed.getCurrentHp()).isEqualTo(50);
        assertThat(parsed.getMaxHp()).isEqualTo(50);
    }

    @Test
    void shouldRoundTripMonsterDespawnedEvent() throws InvalidProtocolBufferException {
        var event = MonsterDespawnedEvent.newBuilder().setMonsterId(-1L).build();
        var header = new PacketHeader(Opcode.OPCODE_MONSTER_DESPAWNED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        var parsed = MonsterDespawnedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getMonsterId()).isEqualTo(-1L);
    }
}
