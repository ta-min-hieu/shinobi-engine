package dev.kage.server.world;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Danh mục quái mà world này được phép spawn (ADR-037 D3) — dữ liệu THUẦN, không biết DB là gì.
 *
 * <p>{@code server-world} không được đọc DB (Điều 23) và {@code server-database} thậm chí không phụ
 * thuộc {@code server-world}; chỉ {@code server-gateway} phụ thuộc cả hai, nên gateway là nơi đọc
 * {@code monster_template}/{@code monster_spawn} rồi dịch sang record này. Đúng khuôn ADR-036 đã
 * dựng cho {@link EquipmentBonusSnapshot}.
 *
 * <p><b>{@link #EMPTY} nghĩa là world không spawn quái nào</b> — đó là hành vi của dungeon/arena
 * (chúng dựng với {@code spawnOverworldContent = false} và boss vào bằng lệnh riêng).
 *
 * @param species mọi loài quái world này biết, tra theo {@code code}
 * @param spawnPoints điểm spawn theo THỨ TỰ; chỉ số trong danh sách này chính là "chỗ cũ" mà
 *     {@code WorldFacade} nhớ để hồi sinh đúng vị trí
 */
public record MonsterCatalogSnapshot(Map<String, MonsterSpecies> species, List<MonsterSpawnDefinition> spawnPoints) {

    /** Không có quái nào — dungeon/arena, và mọi test không quan tâm tới quái. */
    public static final MonsterCatalogSnapshot EMPTY = new MonsterCatalogSnapshot(Map.of(), List.of());

    public MonsterCatalogSnapshot {
        species = Map.copyOf(species);
        spawnPoints = List.copyOf(spawnPoints);
    }

    /**
     * Dựng từ danh sách loài + điểm spawn, tự lập chỉ mục theo {@code code}.
     *
     * <p>Điểm spawn trỏ tới loài KHÔNG có trong {@code speciesList} bị **loại bỏ** thay vì ném lỗi:
     * dữ liệu lệch trong bảng config không đáng làm cả world không khởi động được. FK trong DB đã
     * là hàng rào thứ nhất; đây là hàng rào thứ hai.
     */
    public static MonsterCatalogSnapshot of(List<MonsterSpecies> speciesList, List<MonsterSpawnDefinition> spawns) {
        Map<String, MonsterSpecies> byCode =
                speciesList.stream().collect(Collectors.toMap(MonsterSpecies::code, Function.identity()));
        List<MonsterSpawnDefinition> known = spawns.stream()
                .filter(spawn -> byCode.containsKey(spawn.monsterCode()))
                .toList();
        return new MonsterCatalogSnapshot(byCode, known);
    }

    public Optional<MonsterSpecies> find(String monsterCode) {
        return Optional.ofNullable(species.get(monsterCode));
    }

    public boolean isEmpty() {
        return spawnPoints.isEmpty();
    }

    /**
     * Một loài quái: mã trên dây, khoá art, EXP thưởng, và chỉ số chiến đấu dưới dạng SỐ THUẦN.
     *
     * <p>Cố ý KHÔNG mang {@code CombatConstants.CombatStats}: kiểu đó package-private, nên để nó
     * trong một record công khai là dựng ra một API mà {@code server-gateway} không gọi được —
     * và là rò rỉ nội tình combat ra ngoài module. Gateway chép số, {@link WorldFacade} tự dựng
     * {@code CombatStats}, đúng ranh giới của cả hai.
     *
     * <p>{@code hitPercent}/{@code dodgePercent}/{@code critPercent} là số nguyên 0..100 (chia 100
     * lúc dựng); {@code element} là TÊN enum {@code Element} — chuỗi lạ bị coi là {@code NONE}
     * thay vì làm chết world (xem {@code WorldFacade#toStats}).
     */
    public record MonsterSpecies(
            String code,
            String modelKey,
            int maxHp,
            long expReward,
            MonsterCombatNumbers combat,
            MonsterAiNumbers ai,
            MonsterBossNumbers boss) {

        /** Loài có AI kịch bản hay không — {@code false} nghĩa là chạy FSM thường (ADR-048). */
        public boolean isBoss() {
            return boss != null;
        }
    }

    /**
     * Hành vi kịch bản của một loài boss (ADR-048) — {@code null} với quái thường.
     *
     * <p>Cùng lý do {@link MonsterCombatNumbers} không mang {@code CombatStats}: đây là SỐ THÔ chép
     * qua ranh giới module, không phải kiểu nội bộ của {@code feature-monster}. {@code scriptRef} là
     * đường dẫn classpath; {@code WorldFacade} chép nó vào {@code BossAiComponent} lúc spawn, và
     * {@code BossAiSystem} nạp theo đó — nhờ vậy mỗi boss có hành vi riêng mà Java không biết boss
     * nào tồn tại.
     */
    public record MonsterBossNumbers(String scriptRef, float decisionIntervalSeconds, float skillCooldownSeconds) {}

    /**
     * Tham số AI thô của một loài (ADR-039 D1) — SÁU con số, không phải tám.
     *
     * <p>{@code ARRIVAL_THRESHOLD_METERS} và {@code MAX_PATROL_DURATION_SECONDS} cố ý KHÔNG ở đây:
     * chúng là cơ chế của FSM (epsilon hình học + van chống kẹt), ở lại {@link AiConstants}.
     *
     * <p>{@code aggroRadiusMeters == 0} nghĩa là <b>không bao giờ gây hấn</b> — {@link AiSystem} có
     * nhánh tường minh cho nó, không để nó "tự đúng" nhờ phép so sánh.
     */
    public record MonsterAiNumbers(
            float aggroRadiusMeters,
            float leashRadiusMeters,
            float chaseSpeedMps,
            float patrolSpeedMps,
            float patrolRadiusMeters,
            float idleDurationSeconds) {}

    /** Chỉ số chiến đấu thô của một loài — gộp lại để {@link MonsterSpecies} không quá dài. */
    public record MonsterCombatNumbers(
            int atk,
            int skillDmg,
            int def,
            int res,
            int hitPercent,
            int dodgePercent,
            int critPercent,
            String element) {}

    /** Một điểm spawn: loài nào, ở đâu, hồi sinh sau bao lâu. */
    public record MonsterSpawnDefinition(String monsterCode, float posX, float posY, float respawnSeconds) {}
}
