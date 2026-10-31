package dev.kage.tools.loadtest.probe;

import java.io.IOException;
import java.net.SocketTimeoutException;

import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.TameMonsterCommand;
import dev.kage.protocol.TameMonsterResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.tools.loadtest.GameConnection;

/**
 * Bước thu phục (ADR-124): {@code beatTame:<mã loài>:<giây>} — tìm con quái đầu tiên của loài đó trong gói 402, nhắm
 * và đánh thường cách nhau hơn hồi chiêu tới khi nó còn ≤ 20% máu, rồi dùng Phong Yêu Phù (328). Đặt bước này ĐẦU chuỗi
 * để còn đọc được gói 402 lúc vào world.
 */
final class Lot23TameSteps {

    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long ATTACK_SPACING_MILLIS = 1_200L;
    private static final int TAME_HP_PERCENT = 20;
    private static final int PERCENT = 100;

    private final GameConnection connection;
    private final Lot23Scenario.Asker asker;
    private long target;
    private int maxHp;
    private int currentHp;
    private long nextAttack;
    private int lastHit;

    Lot23TameSteps(GameConnection connection, Lot23Scenario.Asker asker) {
        this.connection = connection;
        this.asker = asker;
    }

    String beatTame(String arg) throws IOException {
        String[] parts = arg.split(":", 2);
        String code = parts[0];
        long deadline = System.currentTimeMillis() + Long.parseLong(parts[1]) * MILLIS_PER_SECOND;
        int hits = 0;
        while (System.currentTimeMillis() < deadline) {
            if (weakEnough()) {
                return "đánh " + hits + " lần, máu " + currentHp + "/" + maxHp + " → " + tame();
            }
            attackIfDue();
            PacketCodec.Frame frame;
            try {
                frame = connection.readFrame();
            } catch (SocketTimeoutException quiet) {
                continue;
            }
            hits += onFrame(frame, code);
            if (target != 0L && currentHp <= 0) {
                return "quái chết trước khi kịp dùng phù (đánh " + hits + " lần)";
            }
        }
        return target == 0L ? "không thấy " + code : "hết giờ, máu " + currentHp + "/" + maxHp;
    }

    private boolean weakEnough() {
        // ADR-124 (2026-10-31): ≤ 20% máu HOẶC còn ít hơn một đòn của chính mình.
        return target != 0L
                && (currentHp * PERCENT <= maxHp * TAME_HP_PERCENT || (lastHit > 0 && currentHp <= lastHit));
    }

    private void attackIfDue() throws IOException {
        if (target != 0L && System.currentTimeMillis() >= nextAttack) {
            connection.send(
                    Opcode.OPCODE_ATTACK_COMMAND_VALUE,
                    AttackCommand.getDefaultInstance().toByteArray());
            nextAttack = System.currentTimeMillis() + ATTACK_SPACING_MILLIS;
        }
    }

    /** @return 1 nếu là một đòn của người chơi trúng mục tiêu */
    private int onFrame(PacketCodec.Frame frame, String code) throws IOException {
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE && target == 0L) {
            MonsterSpawnedEvent spawned = MonsterSpawnedEvent.parseFrom(frame.payload());
            if (code.equals(spawned.getMonsterCode())) {
                target = spawned.getMonsterId();
                maxHp = spawned.getMaxHp();
                currentHp = spawned.getCurrentHp();
                connection.send(
                        Opcode.OPCODE_SET_TARGET_COMMAND_VALUE,
                        SetTargetCommand.newBuilder()
                                .setTargetEntityId(target)
                                .build()
                                .toByteArray());
            }
        } else if (opcode == Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE) {
            AttackResultEvent hit = AttackResultEvent.parseFrom(frame.payload());
            if (hit.getTargetEntityId() == target && hit.getAttackerEntityId() > 0) {
                currentHp = hit.getTargetCurrentHp();
                lastHit = Math.max(lastHit, hit.getDamage());
                return 1;
            }
        }
        return 0;
    }

    private String tame() throws IOException {
        return asker.ask(
                Opcode.OPCODE_TAME_MONSTER_COMMAND_VALUE,
                TameMonsterCommand.newBuilder().setMonsterId(target).build(),
                Opcode.OPCODE_TAME_MONSTER_RESPONSE_VALUE,
                p -> {
                    TameMonsterResponse r = TameMonsterResponse.parseFrom(p);
                    return r.getOutcome().name() + " tpl" + r.getPetItemTemplateId() + " thư=" + r.getMailed();
                });
    }
}
