package dev.kage.tools.loadtest.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Tổng hợp ảnh chụp thế giới — thuần logic, không cần server. Kịch bản dựng lại đúng câu hỏi còn
 * treo ở ROADMAP mục 58: V29 đặt spawn ở x = 21/34/47, người chơi đứng ở x=24, hỏi có mấy con
 * TRONG TẦM NHÌN và ở đâu.
 */
final class WorldObservationTest {

    private static final long BANDIT_A = -1L;
    private static final long BANDIT_B = -2L;
    private static final long PLAYER_ID = 3L;

    private final WorldObservation observation = new WorldObservation();

    private static PacketCodec.Frame frameOf(int opcode, MessageLite message) {
        return PacketCodec.decodeFrame(
                PacketCodec.encodeFrame(new PacketHeader(opcode, ProtocolVersion.CURRENT, 1), message.toByteArray()));
    }

    private void apply(long elapsedMillis, int opcode, MessageLite message) throws InvalidProtocolBufferException {
        observation.apply(elapsedMillis, frameOf(opcode, message));
    }

    private static MonsterSpawnedEvent bandit(long id, float x) {
        return MonsterSpawnedEvent.newBuilder()
                .setMonsterId(id)
                .setMonsterCode("wandering_bandit")
                .setModelKey("mob_wandering-bandit")
                .setX(x)
                .setY(0f)
                .setCurrentHp(120)
                .setMaxHp(120)
                .build();
    }

    private void enterWorldAt(float x) {
        observation.enterWorld(
                CharacterSummary.newBuilder()
                        .setId(PLAYER_ID)
                        .setName("Kagemaru")
                        .build(),
                SelectCharacterResponse.newBuilder()
                        .setSuccess(true)
                        .setSpawnX(x)
                        .setSpawnY(0f)
                        .setCurrentHp(100)
                        .setMaxHp(100)
                        .build());
    }

    @Test
    void shouldStartAtSpawnPositionFromSelectCharacterResponse() {
        enterWorldAt(24f);

        assertThat(observation.playerX()).isEqualTo(24f);
    }

    @Test
    void shouldListVisibleMonstersNearestFirst_withRealDistances() throws InvalidProtocolBufferException {
        enterWorldAt(24f);
        apply(100L, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE, bandit(BANDIT_A, 21f));
        apply(120L, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE, bandit(BANDIT_B, 34f));

        assertThat(observation.monstersInAreaOfInterest())
                .extracting(WorldObservation.MonsterSighting::monsterId)
                .containsExactly(BANDIT_A, BANDIT_B);
        assertThat(observation.distanceToPlayer(
                        observation.monstersInAreaOfInterest().get(0)))
                .isCloseTo(3.0, within(0.001));
        assertThat(observation.distanceToPlayer(
                        observation.monstersInAreaOfInterest().get(1)))
                .isCloseTo(10.0, within(0.001));
        assertThat(observation.nearestMonsterId()).hasValue(BANDIT_A);
    }

    /**
     * Đây chính là thứ ảnh chụp KHÔNG phân giải được: hai con hiện cùng lúc trên màn hình có thể là
     * một con đứng yên ở spawn và một con đang ĐUỔI từ chỗ khác tới. Toạ độ theo thời gian phân
     * biệt được, ảnh tĩnh thì không.
     */
    @Test
    void shouldTrackMonsterMovingTowardPlayer_separatelyFromItsSpawnPoint() throws InvalidProtocolBufferException {
        enterWorldAt(24f);
        apply(100L, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE, bandit(BANDIT_B, 34f));
        apply(
                900L,
                Opcode.OPCODE_MONSTER_MOVED_EVENT_VALUE,
                MonsterMovedEvent.newBuilder()
                        .setMonsterId(BANDIT_B)
                        .setX(29f)
                        .setY(0f)
                        .build());

        WorldObservation.MonsterSighting chaser =
                observation.monstersInAreaOfInterest().get(0);
        assertThat(chaser.x()).isEqualTo(29f);
        assertThat(chaser.firstSeenMillis()).isEqualTo(100L);
        assertThat(chaser.lastUpdateMillis()).isEqualTo(900L);
        assertThat(observation.distanceToPlayer(chaser)).isCloseTo(5.0, within(0.001));
    }

    @Test
    void shouldKeepDespawnedMonsterInHistory_butOutOfVisibleList() throws InvalidProtocolBufferException {
        enterWorldAt(24f);
        apply(100L, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE, bandit(BANDIT_A, 21f));
        apply(
                500L,
                Opcode.OPCODE_MONSTER_DESPAWNED_EVENT_VALUE,
                MonsterDespawnedEvent.newBuilder().setMonsterId(BANDIT_A).build());

        assertThat(observation.monstersInAreaOfInterest()).isEmpty();
        assertThat(observation.renderReport()).contains("tổng đã từng thấy: 1");
    }

    @Test
    void shouldRecordEveryBlow_withDamageAndRemainingHp() throws InvalidProtocolBufferException {
        enterWorldAt(21f);
        apply(100L, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE, bandit(BANDIT_A, 21f));
        apply(
                1000L,
                Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE,
                AttackResultEvent.newBuilder()
                        .setAttackerEntityId(PLAYER_ID)
                        .setTargetEntityId(BANDIT_A)
                        .setDamage(17)
                        .setTargetCurrentHp(103)
                        .build());
        apply(
                2000L,
                Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE,
                AttackResultEvent.newBuilder()
                        .setAttackerEntityId(BANDIT_A)
                        .setTargetEntityId(PLAYER_ID)
                        .setDamage(9)
                        .setTargetCurrentHp(91)
                        .build());

        assertThat(observation.damageEvents())
                .extracting(
                        WorldObservation.DamageEvent::attackerEntityId,
                        WorldObservation.DamageEvent::targetEntityId,
                        WorldObservation.DamageEvent::damage)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PLAYER_ID, BANDIT_A, 17),
                        org.assertj.core.groups.Tuple.tuple(BANDIT_A, PLAYER_ID, 9));
        assertThat(observation.monstersInAreaOfInterest().get(0).currentHp()).isEqualTo(103);
        assertThat(observation.renderReport()).contains("máu=91/100");
    }

    @Test
    void shouldFollowPlayerPositionFromMoveResponse() throws InvalidProtocolBufferException {
        enterWorldAt(8f);
        apply(
                300L,
                Opcode.OPCODE_MOVE_RESPONSE_VALUE,
                MoveResponse.newBuilder().setX(23.75f).setY(0f).build());

        assertThat(observation.playerX()).isEqualTo(23.75f);
    }

    @Test
    void shouldDropMonsterOutOfSight_whenItDies() throws InvalidProtocolBufferException {
        enterWorldAt(21f);
        apply(100L, Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE, bandit(BANDIT_A, 21f));
        apply(
                1000L,
                Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE,
                AttackResultEvent.newBuilder()
                        .setAttackerEntityId(PLAYER_ID)
                        .setTargetEntityId(BANDIT_A)
                        .setDamage(120)
                        .setTargetCurrentHp(0)
                        .setTargetDied(true)
                        .build());

        assertThat(observation.monstersInAreaOfInterest()).isEmpty();
        assertThat(observation.nearestMonsterId()).isEmpty();
    }
}
