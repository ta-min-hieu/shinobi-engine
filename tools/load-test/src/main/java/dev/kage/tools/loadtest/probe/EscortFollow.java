package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.EscortStateEvent;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Bước {@code escortFollow:<giây>} (ADR-114): đi kèm đoàn hộ tống thay vì đứng nhìn — đoàn chỉ đi khi người
 * hộ tống trong 6 m. Quái nào lại gần đoàn thì bị chọn làm mục tiêu và đánh; không có quái thì đứng nhỉnh
 * hơn đoàn một chút về phía đích. Dừng khi server báo đoàn ra khỏi MOVING/WAITING (tới nơi, vỡ, bỏ).
 */
final class EscortFollow {

    private static final int MAX_FRAMES_TO_SKIP = 2000;
    private static final long ATTACK_SPACING_MILLIS = 1_100L;
    private static final long MOVE_SPACING_MILLIS = 100L;
    private static final long MILLIS_PER_SECOND = 1_000L;
    /** Dưới tầm đánh thường 2 m của server, chừa sai số vị trí. */
    private static final float IN_RANGE_METERS = 1.5f;
    /** Quái trong khoảng này quanh đoàn bị coi là mối đe doạ (đoàn chỉ ăn đòn trong 2 m, chừa đường tiến). */
    private static final float THREAT_RADIUS_METERS = 6f;
    /** Đứng trước đoàn chừng này mét — quái trên đường gặp người trước khi gặp xe. */
    private static final float LEAD_METERS = 1.5f;
    /** Dưới mức này coi như đã đứng đúng chỗ, gửi lệnh đứng yên. */
    private static final float STAND_TOLERANCE_METERS = 0.5f;

    private final GameConnection connection;
    private final Map<Long, Float> monsterX = new HashMap<>();
    private float playerX;
    private float caravanX = Float.NaN;
    private float endX = Float.NaN;
    private String state = "";
    private String lastLine = "không thấy gói 625";
    private int events;
    private int kills;
    private int lowestHp = Integer.MAX_VALUE;
    private long target;
    private long nextAttack;

    EscortFollow(GameConnection connection) {
        this.connection = connection;
    }

    String run(int seconds) throws IOException {
        long deadline = System.currentTimeMillis() + seconds * MILLIS_PER_SECOND;
        while (System.currentTimeMillis() < deadline && !finished()) {
            if (Float.isNaN(caravanX)) {
                readOne();
            } else {
                act();
            }
        }
        return (finished() ? "KẾT THÚC " : "hết giờ ") + "gói=" + events + " hạ=" + kills + " máuThấpNhất="
                + (lowestHp == Integer.MAX_VALUE ? "?" : lowestHp) + " cuối: " + lastLine;
    }

    private boolean finished() {
        return !state.isEmpty() && !"MOVING".equals(state) && !"WAITING".equals(state);
    }

    private void act() throws IOException {
        long threat = nearestThreat();
        if (threat != target) {
            target = threat;
            if (target != 0L) {
                connection.send(
                        Opcode.OPCODE_SET_TARGET_COMMAND_VALUE,
                        SetTargetCommand.newBuilder()
                                .setTargetEntityId(target)
                                .build()
                                .toByteArray());
            }
        }
        float goal = target != 0L ? monsterX.get(target) : caravanX + Math.signum(endX - caravanX) * LEAD_METERS;
        float tolerance = target != 0L ? IN_RANGE_METERS : STAND_TOLERANCE_METERS;
        float gap = goal - playerX;
        boolean far = Math.abs(gap) > tolerance;
        pause();
        connection.send(
                Opcode.OPCODE_MOVE_COMMAND_VALUE,
                MoveCommand.newBuilder()
                        .setDirX(far ? Math.signum(gap) : 0f)
                        .build()
                        .toByteArray());
        awaitMoveResponse();
        if (!far && target != 0L && System.currentTimeMillis() >= nextAttack) {
            connection.send(
                    Opcode.OPCODE_ATTACK_COMMAND_VALUE,
                    AttackCommand.getDefaultInstance().toByteArray());
            nextAttack = System.currentTimeMillis() + ATTACK_SPACING_MILLIS;
        }
    }

    private long nearestThreat() {
        long best = 0L;
        float bestGap = THREAT_RADIUS_METERS;
        for (Map.Entry<Long, Float> monster : monsterX.entrySet()) {
            float gap = Math.abs(monster.getValue() - caravanX);
            if (gap <= bestGap) {
                bestGap = gap;
                best = monster.getKey();
            }
        }
        return best;
    }

    private void pause() {
        try {
            Thread.sleep(MOVE_SPACING_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void readOne() throws IOException {
        try {
            onFrame(connection.readFrame());
        } catch (SocketTimeoutException quiet) {
            return; // chưa có gói nào — vòng ngoài tự kiểm hạn giờ
        }
    }

    private void awaitMoveResponse() throws IOException {
        for (int skipped = 0; skipped < MAX_FRAMES_TO_SKIP; skipped++) {
            PacketCodec.Frame frame = connection.readFrame();
            onFrame(frame);
            if (frame.header().opcode() == Opcode.OPCODE_MOVE_RESPONSE_VALUE) {
                return;
            }
        }
    }

    private void onFrame(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_ESCORT_STATE_EVENT_VALUE) {
            onEscort(EscortStateEvent.parseFrom(frame.payload()));
        } else if (opcode == Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE) {
            MonsterSpawnedEvent spawned = MonsterSpawnedEvent.parseFrom(frame.payload());
            monsterX.put(spawned.getMonsterId(), spawned.getX());
        } else if (opcode == Opcode.OPCODE_MONSTER_MOVED_EVENT_VALUE) {
            MonsterMovedEvent moved = MonsterMovedEvent.parseFrom(frame.payload());
            monsterX.put(moved.getMonsterId(), moved.getX());
        } else if (opcode == Opcode.OPCODE_MONSTER_DESPAWNED_EVENT_VALUE) {
            monsterX.remove(MonsterDespawnedEvent.parseFrom(frame.payload()).getMonsterId());
        } else if (opcode == Opcode.OPCODE_MOVE_RESPONSE_VALUE) {
            playerX = MoveResponse.parseFrom(frame.payload()).getX();
        } else if (opcode == Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE) {
            AttackResultEvent hit = AttackResultEvent.parseFrom(frame.payload());
            if (hit.getTargetDied() && monsterX.remove(hit.getTargetEntityId()) != null) {
                kills++;
            }
        }
    }

    private void onEscort(EscortStateEvent escort) {
        events++;
        caravanX = escort.getX();
        endX = escort.getEndX();
        state = escort.getState();
        lowestHp = Math.min(lowestHp, escort.getHp());
        lastLine = state + " x=" + Math.round(caravanX) + "/" + Math.round(endX) + " máu=" + escort.getHp() + "/"
                + escort.getMaxHp();
    }
}
