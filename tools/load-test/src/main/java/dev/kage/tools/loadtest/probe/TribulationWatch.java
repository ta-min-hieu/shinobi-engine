package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.net.SocketTimeoutException;

import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.ExperienceChangedEvent;
import dev.kage.protocol.HealthChangedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.TribulationEvent;
import dev.kage.protocol.TribulationEventKind;
import dev.kage.protocol.WorldChangedEvent;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Bước {@code dokiep}/{@code dokiepIdle} của probe (ADR-126, ADR-146): sau {@code /dokiep}, ghi mọi gói 332 + máu +
 * đổi world + EXP thành một dòng. Tâm ma hiện thì chốt nó làm mục tiêu và đánh thường mỗi
 * {@value #ATTACK_MILLIS} ms (trên hồi chiêu 1 giây — đánh dày hơn thì mất nửa số nhát) tới khi nó tan, trừ khi
 * {@code fightDemon} tắt (thử nhánh hết giờ).
 */
final class TribulationWatch {

    private static final long ATTACK_MILLIS = 1_100L;
    private static final String HEART_DEMON_PREFIX = "heart_demon_";

    private final GameConnection connection;
    private final boolean fightDemon;
    private final StringBuilder seen;
    private long demonId;
    private boolean demonAwake;
    private long nextAttackAt;

    TribulationWatch(GameConnection connection, boolean fightDemon, String outcome) {
        this.connection = connection;
        this.fightDemon = fightDemon;
        this.seen = new StringBuilder(outcome);
    }

    /** Đọc tới {@code deadlineMillis} rồi trả dòng đã ghi. */
    String run(long deadlineMillis) throws IOException {
        while (System.currentTimeMillis() < deadlineMillis) {
            attackIfDue();
            PacketCodec.Frame frame;
            try {
                frame = connection.readFrame();
            } catch (SocketTimeoutException quiet) {
                continue;
            }
            record(frame);
        }
        return seen.toString();
    }

    private void attackIfDue() throws IOException {
        if (!fightDemon || !demonAwake || demonId == 0L || System.currentTimeMillis() < nextAttackAt) {
            return;
        }
        connection.send(
                Opcode.OPCODE_SET_TARGET_COMMAND_VALUE,
                SetTargetCommand.newBuilder().setTargetEntityId(demonId).build().toByteArray());
        connection.send(
                Opcode.OPCODE_ATTACK_COMMAND_VALUE,
                AttackCommand.getDefaultInstance().toByteArray());
        nextAttackAt = System.currentTimeMillis() + ATTACK_MILLIS;
    }

    private void record(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_TRIBULATION_EVENT_VALUE) {
            onTribulation(TribulationEvent.parseFrom(frame.payload()));
        } else if (opcode == Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE) {
            onMonsterSpawned(MonsterSpawnedEvent.parseFrom(frame.payload()));
        } else if (opcode == Opcode.OPCODE_HEALTH_CHANGED_EVENT_VALUE) {
            HealthChangedEvent h = HealthChangedEvent.parseFrom(frame.payload());
            seen.append(" máu=").append(h.getCurrentHp()).append('/').append(h.getMaxHp());
        } else if (opcode == Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE) {
            seen.append(" [308 ")
                    .append(WorldChangedEvent.parseFrom(frame.payload())
                            .getWorldKind()
                            .name())
                    .append(']');
        } else if (opcode == Opcode.OPCODE_EXPERIENCE_CHANGED_EVENT_VALUE) {
            seen.append(" [EXP cấp=")
                    .append(ExperienceChangedEvent.parseFrom(frame.payload()).getLevel())
                    .append(']');
        }
    }

    private void onTribulation(TribulationEvent e) {
        seen.append(" [")
                .append(e.getKind().name().replace("TRIBULATION_EVENT_KIND_", ""))
                .append(' ')
                .append(e.getWave())
                .append('/')
                .append(e.getWaves());
        if (e.getSecondsLeft() > 0) {
            seen.append(' ').append(e.getSecondsLeft()).append('s');
        }
        seen.append(']');
        if (e.getKind() == TribulationEventKind.TRIBULATION_EVENT_KIND_HEART_DEMON_APPEARED) {
            demonAwake = true;
        } else if (e.getKind() == TribulationEventKind.TRIBULATION_EVENT_KIND_HEART_DEMON_SLAIN) {
            demonAwake = false;
        }
    }

    private void onMonsterSpawned(MonsterSpawnedEvent m) {
        if (!m.getMonsterCode().startsWith(HEART_DEMON_PREFIX)) {
            return;
        }
        demonId = m.getMonsterId();
        seen.append(" [tâm ma ")
                .append(m.getMonsterCode())
                .append(" máu=")
                .append(m.getCurrentHp())
                .append(']');
    }
}
