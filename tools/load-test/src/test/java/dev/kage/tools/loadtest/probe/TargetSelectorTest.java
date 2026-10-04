package dev.kage.tools.loadtest.probe;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Hai bẫy công cụ cũ của {@code --attackTarget=nearest} — cả hai từng bịa ra trận thua giả. Ô đầu
 * tiên ĐỎ trên cách cũ (chốt một lần trước vòng lặp): probe đứng im suốt cửa sổ quan sát.
 */
final class TargetSelectorTest {

    private static final long PLAYER_ID = 3L;
    private static final long RAIDER = -11L;
    private static final long RAIDER_RESPAWNED = -40L;
    private static final long BANDIT = -4L;

    private final WorldObservation observation = new WorldObservation();

    @BeforeEach
    void enterWorld() {
        observation.enterWorld(
                CharacterSummary.newBuilder()
                        .setId(PLAYER_ID)
                        .setName("QuestRun")
                        .build(),
                SelectCharacterResponse.newBuilder()
                        .setSuccess(true)
                        .setSpawnX(50f)
                        .setCurrentHp(100)
                        .setMaxHp(100)
                        .build());
    }

    private void apply(long elapsedMillis, int opcode, MessageLite message) throws InvalidProtocolBufferException {
        observation.apply(
                elapsedMillis,
                PacketCodec.decodeFrame(PacketCodec.encodeFrame(
                        new PacketHeader(opcode, ProtocolVersion.CURRENT, 1), message.toByteArray())));
    }

    private void spawn(long id, String code, float x) throws InvalidProtocolBufferException {
        apply(
                100L,
                Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE,
                MonsterSpawnedEvent.newBuilder()
                        .setMonsterId(id)
                        .setMonsterCode(code)
                        .setX(x)
                        .setCurrentHp(130)
                        .setMaxHp(130)
                        .build());
    }

    private void kill(long id) throws InvalidProtocolBufferException {
        apply(
                2000L,
                Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE,
                AttackResultEvent.newBuilder()
                        .setAttackerEntityId(PLAYER_ID)
                        .setTargetEntityId(id)
                        .setDamage(130)
                        .setTargetCurrentHp(0)
                        .setTargetDied(true)
                        .build());
    }

    private static ProbeConfig config(String... args) {
        return ProbeConfig.parse(args);
    }

    @Test
    void shouldPickFirstMonster_whenNoneWasVisibleAtStart() throws InvalidProtocolBufferException {
        TargetSelector selector = new TargetSelector(config("--attackTarget=nearest"), observation);
        assertThat(selector.current()).isEmpty();

        spawn(RAIDER, "vein_raider", 47f);

        assertThat(selector.refresh(observation)).hasValue(RAIDER);
        assertThat(selector.current()).hasValue(RAIDER);
        assertThat(selector.refresh(observation)).isEmpty();
    }

    @Test
    void shouldKeepDeadTarget_withoutRetargetFlag() throws InvalidProtocolBufferException {
        spawn(RAIDER, "vein_raider", 47f);
        TargetSelector selector = new TargetSelector(config("--attackTarget=nearest"), observation);
        kill(RAIDER);
        spawn(RAIDER_RESPAWNED, "vein_raider", 47f);

        assertThat(selector.refresh(observation)).isEmpty();
        assertThat(selector.current()).hasValue(RAIDER);
    }

    @Test
    void shouldMoveToNextMonster_whenTargetDiesAndRetargetRequested() throws InvalidProtocolBufferException {
        spawn(RAIDER, "vein_raider", 47f);
        TargetSelector selector =
                new TargetSelector(config("--attackTarget=nearest", "--retargetOnKill=true"), observation);
        kill(RAIDER);
        assertThat(selector.refresh(observation)).isEmpty(); // chưa có con nào khác để chuyển sang

        spawn(RAIDER_RESPAWNED, "vein_raider", 47f);

        assertThat(selector.refresh(observation)).hasValue(RAIDER_RESPAWNED);
    }

    @Test
    void shouldOnlyChooseTheRequestedSpecies() throws InvalidProtocolBufferException {
        spawn(BANDIT, "wandering_bandit", 49f);
        spawn(RAIDER, "vein_raider", 47f);

        TargetSelector selector =
                new TargetSelector(config("--attackTarget=nearest", "--attackCode=vein_raider"), observation);

        assertThat(selector.current()).hasValue(RAIDER);
    }

    @Test
    void shouldNeverChangeAnExplicitTarget() throws InvalidProtocolBufferException {
        TargetSelector selector = new TargetSelector(config("--attackTarget=-7", "--retargetOnKill=true"), observation);
        spawn(RAIDER, "vein_raider", 47f);

        assertThat(selector.refresh(observation)).isEmpty();
        assertThat(selector.current()).hasValue(-7L);
    }
}
