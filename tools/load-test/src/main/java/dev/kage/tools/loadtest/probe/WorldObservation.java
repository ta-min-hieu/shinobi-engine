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
import dev.kage.protocol.ExperienceChangedEvent;
import dev.kage.protocol.HealthChangedEvent;
import dev.kage.protocol.MonsterDespawnedEvent;
import dev.kage.protocol.MonsterMovedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.SkillResultEvent;
import dev.kage.protocol.WorldChangedEvent;
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

    /**
     * EXP của CHÍNH mình lần gần nhất server đẩy xuống ({@code ExperienceChangedEvent}).
     *
     * <p>Không có gói nào để HỎI EXP (protocol chỉ đẩy khi đổi), nên đây là nguồn DUY NHẤT — và vì
     * thế nó rỗng cho tới sự kiện đầu tiên. Kịch bản nhiệm vụ báo cáo đúng như vậy thay vì bịa số 0.
     */
    record ExperienceSnapshot(int level, long totalExp, long expIntoLevel, long expForNextLevel) {}

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
    private int playerLevelAtLogin;
    private float playerX;
    private float playerY;
    private int playerCurrentHp;

    /** Số gói {@code HealthChangedEvent} đã nhận — ADR-046 chốt trần ≤1/giây/entity, đây là chỗ ĐO nó. */
    private int healthChanges;

    private int playerMaxHp;
    private ExperienceSnapshot latestExperience;

    /** Trạng thái ban đầu lấy từ chính {@code SelectCharacterResponse} (ADR-041 spawn_x/spawn_y). */
    void enterWorld(CharacterSummary summary, SelectCharacterResponse response) {
        this.playerCharacterId = summary.getId();
        this.playerName = summary.getName();
        this.playerLevelAtLogin = summary.getLevel();
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
            case Opcode.OPCODE_HEALTH_CHANGED_EVENT_VALUE -> onHealthChanged(HealthChangedEvent.parseFrom(payload));
            case Opcode.OPCODE_WORLD_CHANGED_EVENT_VALUE -> {
                // ADR-051 — vị trí + máu ở World MỚI. Thiếu nhánh này thì sau khi gục trong hầm ngục
                // probe vẫn tin mình đứng ở toạ độ hầm ngục với 0 máu, và walkTo đi lạc hướng.
                WorldChangedEvent event = WorldChangedEvent.parseFrom(payload);
                playerX = event.getX();
                playerY = event.getY();
                playerCurrentHp = event.getCurrentHp();
                playerMaxHp = event.getMaxHp();
                leaveAreaOfInterestOfEveryMonster();
            }
            case Opcode.OPCODE_EXPERIENCE_CHANGED_EVENT_VALUE -> {
                ExperienceChangedEvent event = ExperienceChangedEvent.parseFrom(payload);
                latestExperience = new ExperienceSnapshot(
                        event.getLevel(), event.getTotalExp(), event.getExpIntoLevel(), event.getExpForNextLevel());
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

    /**
     * ADR-046 — máu đổi mà KHÔNG do đòn đánh nào (hồi ngoài chiến đấu, vùng an toàn, đan dược, quái
     * về tới điểm spawn). Không có nhánh này thì probe hoàn toàn MÙ với mọi thứ ADR-046 làm: con số
     * máu cuối báo cáo sẽ là số của đòn đánh cuối cùng, đúng cái bug mà chính ADR đó sửa ở client.
     */
    private void onHealthChanged(HealthChangedEvent event) {
        healthChanges++;
        if (event.getEntityId() == playerCharacterId) {
            playerCurrentHp = event.getCurrentHp();
            if (event.getMaxHp() > 0) {
                playerMaxHp = event.getMaxHp();
            }
            return;
        }
        MonsterSighting target = monsters.get(event.getEntityId());
        if (target != null) {
            monsters.put(
                    event.getEntityId(),
                    withPresence(target, event.getCurrentHp(), target.lastUpdateMillis(), target.inAreaOfInterest()));
        }
    }

    /**
     * Đổi World ⇒ mọi quái đang thấy thuộc World CŨ, server không gửi despawn cho từng con (ADR-051:
     * client tự dọn theo 308). Giữ lại trong báo cáo ("tổng đã từng thấy") nhưng không còn trong tầm
     * nhìn — nếu không, báo cáo hầm ngục liệt kê cả quái làng như đang đứng cạnh boss.
     */
    private void leaveAreaOfInterestOfEveryMonster() {
        monsters.replaceAll(
                (id, sighting) -> withPresence(sighting, sighting.currentHp(), sighting.lastUpdateMillis(), false));
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

    /** Cấp độ server khai trong {@code ListCharactersResponse} lúc chọn nhân vật. */
    int playerLevelAtLogin() {
        return playerLevelAtLogin;
    }

    /** Rỗng khi chưa có {@code ExperienceChangedEvent} nào — xem javadoc {@link ExperienceSnapshot}. */
    Optional<ExperienceSnapshot> latestExperience() {
        return Optional.ofNullable(latestExperience);
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

    /**
     * Số đòn ĐÁNH THƯỜNG do chính nhân vật ra mà server đã xác nhận (trúng hay hụt đều tính — cả
     * hai đều tiêu cooldown). {@link AttackCadence} dùng để biết nhát mở màn đã ra hay chưa.
     */
    int ownBasicAttacksResolved() {
        int count = 0;
        for (DamageEvent event : damageEvents) {
            if (event.attackerEntityId() == playerCharacterId
                    && event.skillCode().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** Con quái gần nhất ĐANG trong tầm nhìn — rỗng nếu chưa thấy con nào. */
    OptionalLong nearestMonsterId() {
        return nearestMonsterId("");
    }

    /**
     * Con quái gần nhất ĐANG trong tầm nhìn thuộc loài {@code monsterCode} — rỗng nếu chưa thấy con
     * nào. {@code monsterCode} rỗng = mọi loài.
     */
    OptionalLong nearestMonsterId(String monsterCode) {
        Optional<MonsterSighting> nearest = monstersInAreaOfInterest().stream()
                .filter(sighting -> monsterCode.isEmpty() || monsterCode.equals(sighting.monsterCode()))
                .findFirst();
        return nearest.map(sighting -> OptionalLong.of(sighting.monsterId())).orElseGet(OptionalLong::empty);
    }

    /** {@code true} khi con quái này còn trong tầm nhìn (chưa chết, chưa despawn, chưa đổi World). */
    boolean isInAreaOfInterest(long monsterId) {
        MonsterSighting sighting = monsters.get(monsterId);
        return sighting != null && sighting.inAreaOfInterest();
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
        report.append(String.format(Locale.ROOT, "Gói hồi máu (408) đã nhận: %d", healthChanges))
                .append(System.lineSeparator());
        appendExperienceSection(report);
        appendMonsterSection(report);
        appendDamageSection(report);
        return report.toString();
    }

    private void appendExperienceSection(StringBuilder report) {
        report.append("EXP: ")
                .append(latestExperience()
                        .map(exp -> String.format(
                                Locale.ROOT,
                                "cấp=%d tổng=%d trongCấp=%d cầnLênCấp=%d",
                                exp.level(),
                                exp.totalExp(),
                                exp.expIntoLevel(),
                                exp.expForNextLevel()))
                        // Không bịa 0: server chỉ đẩy EXPERIENCE_CHANGED khi EXP ĐỔI, im lặng
                        // nghĩa là "chưa đo được", không phải "bằng không".
                        .orElse("chưa có EXPERIENCE_CHANGED nào trong lượt này (cấp lúc đăng nhập=" + playerLevelAtLogin
                                + ")"))
                .append(System.lineSeparator());
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
