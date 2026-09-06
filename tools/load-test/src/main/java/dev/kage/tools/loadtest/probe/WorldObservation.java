package dev.kage.tools.loadtest.probe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.CharacterRespawnedEvent;
import dev.kage.protocol.CharacterSummary;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.SkillResultEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Ảnh chụp SỐNG của thế giới theo đúng những gì server đã đẩy xuống dây — không suy diễn, không
 * nội suy. Dựng lên để trả lời hai câu hỏi mà buổi chơi tay không phân giải được (ROADMAP mục 58):
 * đứng ở toạ độ X thì CÓ MẤY con quái trong tầm nhìn và ở đâu; mỗi đòn đánh gây bao nhiêu sát
 * thương lên ai.
 *
 * <p>Thuần logic, không I/O — {@code WorldObservationTest} chạy nó không cần server.
 *
 * <p>Không thread-safe: 1 probe = 1 luồng đọc.
 */
final class WorldObservation {

    /**
     * Một con quái probe đã NHÌN THẤY. {@code inAreaOfInterest} phân biệt "đang trong tầm nhìn" với
     * "đã từng thấy rồi rời đi/chết" — phân biệt này chính là câu hỏi của ROADMAP mục 58, nên không
     * được xoá khỏi bảng khi despawn.
     */
    record MonsterSighting(
            long monsterId,
            String monsterCode,
            String modelKey,
            float x,
            float y,
            int currentHp,
            int maxHp,
            long firstSeenMillis,
            long lastUpdateMillis,
            boolean inAreaOfInterest) {}

    /** Một đòn đánh THẬT đã quan sát được. {@code skillCode} rỗng = đánh thường. */
    record DamageEvent(
            long elapsedMillis,
            long attackerEntityId,
            long targetEntityId,
            int damage,
            boolean crit,
            boolean miss,
            int targetHpAfter,
            boolean targetDied,
            String skillCode) {}

    private final Map<Long, MonsterSighting> monsters = new LinkedHashMap<>();
    private final List<DamageEvent> damageEvents = new ArrayList<>();

    private long playerCharacterId;
    private String playerName = "";
    private float playerX;
    private float playerY;
    private int playerCurrentHp;
    private int playerMaxHp;

    /** Trạng thái ban đầu lấy từ chính {@code SelectCharacterResponse} (ADR-041 spawn_x/spawn_y). */
    void enterWorld(CharacterSummary summary, SelectCharacterResponse response) {
        this.playerCharacterId = summary.getId();
        this.playerName = summary.getName();
        this.playerX = response.getSpawnX();
        this.playerY = response.getSpawnY();
        this.playerCurrentHp = response.getCurrentHp();
        this.playerMaxHp = response.getMaxHp();
    }

    /**
     * @throws InvalidProtocolBufferException nếu payload không khớp opcode — bug giao thức thật,
     *     không nuốt
     */
    void apply(long elapsedMillis, PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        int opcode = frame.header().opcode();
        if (applyMonsterEvent(elapsedMillis, opcode, frame.payload())) {
            return;
        }
        if (applySelfEvent(opcode, frame.payload())) {
            return;
        }
        applyDamageEvent(elapsedMillis, opcode, frame.payload());
    }

    private boolean applyMonsterEvent(long elapsedMillis, int opcode, byte[] payload)
            throws InvalidProtocolBufferException {
        switch (opcode) {
            case Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE ->
                onMonsterSpawned(elapsedMillis, MonsterSpawnedEvent.parseFrom(payload));
            case Opcode.OPCODE_MONSTER_MOVED_EVENT_VALUE ->
                onMonsterMoved(elapsedMillis, MonsterMovedEvent.parseFrom(payload));
            case Opcode.OPCODE_MONSTER_DESPAWNED_EVENT_VALUE ->
                onMonsterDespawned(
                        elapsedMillis, MonsterDespawnedEvent.parseFrom(payload).getMonsterId());
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean applySelfEvent(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        switch (opcode) {
            case Opcode.OPCODE_MOVE_RESPONSE_VALUE -> {
                MoveResponse response = MoveResponse.parseFrom(payload);
                playerX = response.getX();
                playerY = response.getY();
            }
            case Opcode.OPCODE_CHARACTER_RESPAWNED_EVENT_VALUE -> {
                CharacterRespawnedEvent event = CharacterRespawnedEvent.parseFrom(payload);
                playerX = event.getX();
                playerY = event.getY();
                playerCurrentHp = event.getCurrentHp();
                playerMaxHp = event.getMaxHp();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private void applyDamageEvent(long elapsedMillis, int opcode, byte[] payload)
            throws InvalidProtocolBufferException {
        switch (opcode) {
            case Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE -> {
                AttackResultEvent event = AttackResultEvent.parseFrom(payload);
                recordDamage(new DamageEvent(
                        elapsedMillis,
                        event.getAttackerEntityId(),
                        event.getTargetEntityId(),
                        event.getDamage(),
                        event.getIsCrit(),
                        event.getIsMiss(),
                        event.getTargetCurrentHp(),
                        event.getTargetDied(),
                        ""));
            }
            case Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE -> {
                SkillResultEvent event = SkillResultEvent.parseFrom(payload);
                recordDamage(new DamageEvent(
                        elapsedMillis,
                        event.getAttackerEntityId(),
                        event.getTargetEntityId(),
                        event.getDamage(),
                        event.getIsCrit(),
                        event.getIsMiss(),
                        event.getTargetCurrentHp(),
                        event.getTargetDied(),
                        event.getSkillCode()));
            }
            default -> {
                // Opcode không liên quan tới ảnh chụp thế giới — ProbeEventLine vẫn ghi nó ra dòng.
            }
        }
    }

    private void onMonsterSpawned(long elapsedMillis, MonsterSpawnedEvent event) {
        MonsterSighting previous = monsters.get(event.getMonsterId());
        long firstSeen = previous == null ? elapsedMillis : previous.firstSeenMillis();
        monsters.put(
                event.getMonsterId(),
                new MonsterSighting(
                        event.getMonsterId(),
                        event.getMonsterCode(),
                        event.getModelKey(),
                        event.getX(),
                        event.getY(),
                        event.getCurrentHp(),
                        event.getMaxHp(),
                        firstSeen,
                        elapsedMillis,
                        true));
    }

    private void onMonsterMoved(long elapsedMillis, MonsterMovedEvent event) {
        MonsterSighting known = monsters.get(event.getMonsterId());
        if (known == null) {
            return; // di chuyển của con chưa từng spawn về phía ta — không dựng bản ghi nửa vời.
        }
        monsters.put(
                event.getMonsterId(),
                new MonsterSighting(
                        known.monsterId(),
                        known.monsterCode(),
                        known.modelKey(),
                        event.getX(),
                        event.getY(),
                        known.currentHp(),
                        known.maxHp(),
                        known.firstSeenMillis(),
                        elapsedMillis,
                        true));
    }

    private void onMonsterDespawned(long elapsedMillis, long monsterId) {
        MonsterSighting known = monsters.get(monsterId);
        if (known == null) {
            return;
        }
        monsters.put(monsterId, withPresence(known, known.currentHp(), elapsedMillis, false));
    }

    private void recordDamage(DamageEvent event) {
        damageEvents.add(event);
        MonsterSighting target = monsters.get(event.targetEntityId());
        if (target != null) {
            monsters.put(
                    event.targetEntityId(),
                    withPresence(
                            target,
                            event.targetHpAfter(),
                            event.elapsedMillis(),
                            target.inAreaOfInterest() && !event.targetDied()));
        }
        if (event.targetEntityId() == playerCharacterId) {
            playerCurrentHp = event.targetHpAfter();
        }
    }

    private static MonsterSighting withPresence(
            MonsterSighting sighting, int currentHp, long lastUpdateMillis, boolean inAreaOfInterest) {
        return new MonsterSighting(
                sighting.monsterId(),
                sighting.monsterCode(),
                sighting.modelKey(),
                sighting.x(),
                sighting.y(),
                currentHp,
                sighting.maxHp(),
                sighting.firstSeenMillis(),
                lastUpdateMillis,
                inAreaOfInterest);
    }

    float playerX() {
        return playerX;
    }

    List<MonsterSighting> monstersInAreaOfInterest() {
        return monsters.values().stream()
                .filter(MonsterSighting::inAreaOfInterest)
                .sorted(Comparator.comparingDouble(this::distanceToPlayer))
                .toList();
    }

    List<DamageEvent> damageEvents() {
        return List.copyOf(damageEvents);
    }

    /** Con quái gần nhất ĐANG trong tầm nhìn — rỗng nếu chưa thấy con nào. */
    OptionalLong nearestMonsterId() {
        Optional<MonsterSighting> nearest = monstersInAreaOfInterest().stream().findFirst();
        return nearest.map(sighting -> OptionalLong.of(sighting.monsterId())).orElseGet(OptionalLong::empty);
    }

    double distanceToPlayer(MonsterSighting sighting) {
        return Math.hypot((double) sighting.x() - playerX, (double) sighting.y() - playerY);
    }

    /** Báo cáo cuối, dạng người đọc — mỗi con số đều đến thẳng từ dây, không suy diễn. */
    String renderReport() {
        StringBuilder report = new StringBuilder(512);
        report.append("=== BÁO CÁO PROBE ===").append(System.lineSeparator());
        report.append(String.format(
                        Locale.ROOT,
                        "Nhân vật: id=%d tên=%s vị trí=(%.2f, %.2f) máu=%d/%d",
                        playerCharacterId,
                        playerName,
                        playerX,
                        playerY,
                        playerCurrentHp,
                        playerMaxHp))
                .append(System.lineSeparator());
        appendMonsterSection(report);
        appendDamageSection(report);
        return report.toString();
    }

    private void appendMonsterSection(StringBuilder report) {
        List<MonsterSighting> visible = monstersInAreaOfInterest();
        report.append("Quái TRONG TẦM NHÌN lúc kết thúc: ")
                .append(visible.size())
                .append(" (tổng đã từng thấy: ")
                .append(monsters.size())
                .append(')')
                .append(System.lineSeparator());
        for (MonsterSighting sighting : monsters.values()) {
            report.append(String.format(
                            Locale.ROOT,
                            "  id=%d code=%s vị trí=(%.2f, %.2f) khoảng cách=%.2fm máu=%d/%d trongTầmNhìn=%b"
                                    + " lầnĐầuThấy=%dms",
                            sighting.monsterId(),
                            sighting.monsterCode(),
                            sighting.x(),
                            sighting.y(),
                            distanceToPlayer(sighting),
                            sighting.currentHp(),
                            sighting.maxHp(),
                            sighting.inAreaOfInterest(),
                            sighting.firstSeenMillis()))
                    .append(System.lineSeparator());
        }
    }

    private void appendDamageSection(StringBuilder report) {
        report.append("Đòn đánh đã quan sát: ").append(damageEvents.size()).append(System.lineSeparator());
        for (DamageEvent event : damageEvents) {
            report.append(String.format(
                            Locale.ROOT,
                            "  t=%dms %d -> %d sátThương=%d chíMạng=%b né=%b máuCònLại=%d chết=%b kỹNăng=%s",
                            event.elapsedMillis(),
                            event.attackerEntityId(),
                            event.targetEntityId(),
                            event.damage(),
                            event.crit(),
                            event.miss(),
                            event.targetHpAfter(),
                            event.targetDied(),
                            event.skillCode().isEmpty() ? "(đánh thường)" : event.skillCode()))
                    .append(System.lineSeparator());
        }
    }
}
