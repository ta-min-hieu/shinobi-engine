package dev.kage.protocol.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.SkillResultEvent;
import dev.kage.protocol.UseSkillCommand;
import org.junit.jupiter.api.Test;

/**
 * Round-trip test bắt buộc theo shared/CLAUDE.md khi thêm packet mới — verify {@code
 * PacketCodec} + protobuf thật cho toàn bộ packet Combat/Skill (400-499, NETWORK.md §4).
 */
class CombatPacketsTest {

    @Test
    void shouldRoundTripSetTargetCommand() throws InvalidProtocolBufferException {
        SetTargetCommand command = SetTargetCommand.newBuilder().setTargetEntityId(-1L).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, ProtocolVersion.CURRENT, 5);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        PacketCodec.Frame decoded = PacketCodec.decodeFrame(frameBytes);
        SetTargetCommand parsed = SetTargetCommand.parseFrom(decoded.payload());

        assertThat(decoded.header()).isEqualTo(header);
        assertThat(parsed.getTargetEntityId()).isEqualTo(-1L);
    }

    @Test
    void shouldRoundTripAttackResultEvent() throws InvalidProtocolBufferException {
        AttackResultEvent event = AttackResultEvent.newBuilder()
                .setAttackerEntityId(1L)
                .setTargetEntityId(-1L)
                .setDamage(12)
                .setIsCrit(true)
                .setIsMiss(false)
                .setTargetCurrentHp(38)
                .setTargetDied(false)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        AttackResultEvent parsed = AttackResultEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

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
        MonsterSpawnedEvent event = MonsterSpawnedEvent.newBuilder()
                .setMonsterId(-1L)
                .setMonsterCode("training_dummy")
                .setX(5f)
                .setY(2f)
                .setCurrentHp(50)
                .setMaxHp(50)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        MonsterSpawnedEvent parsed = MonsterSpawnedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getMonsterId()).isEqualTo(-1L);
        assertThat(parsed.getMonsterCode()).isEqualTo("training_dummy");
        assertThat(parsed.getX()).isEqualTo(5f);
        assertThat(parsed.getY()).isEqualTo(2f);
        assertThat(parsed.getCurrentHp()).isEqualTo(50);
        assertThat(parsed.getMaxHp()).isEqualTo(50);
    }

    @Test
    void shouldRoundTripMonsterDespawnedEvent() throws InvalidProtocolBufferException {
        MonsterDespawnedEvent event = MonsterDespawnedEvent.newBuilder().setMonsterId(-1L).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_MONSTER_DESPAWNED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        MonsterDespawnedEvent parsed = MonsterDespawnedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getMonsterId()).isEqualTo(-1L);
    }

    @Test
    void shouldRoundTripMonsterMovedEvent() throws InvalidProtocolBufferException {
        MonsterMovedEvent event = MonsterMovedEvent.newBuilder().setMonsterId(-1L).setX(6.5f).setY(4f).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_MONSTER_MOVED_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        MonsterMovedEvent parsed = MonsterMovedEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getMonsterId()).isEqualTo(-1L);
        assertThat(parsed.getX()).isEqualTo(6.5f);
        assertThat(parsed.getY()).isEqualTo(4f);
    }

    @Test
    void shouldRoundTripUseSkillCommand() throws InvalidProtocolBufferException {
        UseSkillCommand command = UseSkillCommand.newBuilder().setSkillCode("fireball").setTargetEntityId(-1L).build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_USE_SKILL_COMMAND_VALUE, ProtocolVersion.CURRENT, 7);

        byte[] frameBytes = PacketCodec.encodeFrame(header, command.toByteArray());
        PacketCodec.Frame decoded = PacketCodec.decodeFrame(frameBytes);
        UseSkillCommand parsed = UseSkillCommand.parseFrom(decoded.payload());

        assertThat(decoded.header()).isEqualTo(header);
        assertThat(parsed.getSkillCode()).isEqualTo("fireball");
        assertThat(parsed.getTargetEntityId()).isEqualTo(-1L);
    }

    @Test
    void shouldRoundTripSkillResultEvent() throws InvalidProtocolBufferException {
        SkillResultEvent event = SkillResultEvent.newBuilder()
                .setSkillCode("fireball")
                .setAttackerEntityId(1L)
                .setTargetEntityId(-1L)
                .setDamage(20)
                .setIsCrit(false)
                .setIsMiss(false)
                .setTargetCurrentHp(30)
                .setTargetDied(false)
                .build();
        PacketHeader header = new PacketHeader(Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE, ProtocolVersion.CURRENT, 0);

        byte[] frameBytes = PacketCodec.encodeFrame(header, event.toByteArray());
        SkillResultEvent parsed = SkillResultEvent.parseFrom(PacketCodec.decodeFrame(frameBytes).payload());

        assertThat(parsed.getSkillCode()).isEqualTo("fireball");
        assertThat(parsed.getAttackerEntityId()).isEqualTo(1L);
        assertThat(parsed.getTargetEntityId()).isEqualTo(-1L);
        assertThat(parsed.getDamage()).isEqualTo(20);
        assertThat(parsed.getIsCrit()).isFalse();
        assertThat(parsed.getIsMiss()).isFalse();
        assertThat(parsed.getTargetCurrentHp()).isEqualTo(30);
        assertThat(parsed.getTargetDied()).isFalse();
    }
}
