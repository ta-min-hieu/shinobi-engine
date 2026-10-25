package dev.kage.world.domain;

import java.util.List;
import java.util.Optional;

/**
 * Trấn Yêu Tháp (ADR-113) — hiện thực ở server-database. Tầng là dữ liệu ({@code tower_floor}), thưởng mốc ở
 * {@code tower_reward}; {@code character_tower.best_floor} chỉ tăng.
 */
public interface TowerPort {

    /**
     * Một tầng: {@code monsterCount} con loài {@code monsterCode}.
     *
     * @param floor số tầng, từ 1
     */
    record Floor(int floor, String monsterCode, int monsterCount) {}

    /** Thưởng lần đầu qua tầng mốc; {@code itemTemplateId == 0} = chỉ vàng. */
    record Reward(int floor, long gold, long itemTemplateId, int quantity) {}

    /** Một dòng bảng xếp hạng tầng. */
    record Climber(String characterName, int bestFloor) {}

    Optional<Floor> floor(int floor);

    /** Số tầng cao nhất đã có dữ liệu. */
    int topFloor();

    /** Tầng cao nhất đã qua (0 = chưa qua tầng nào). */
    int bestFloorOf(long characterId);

    /**
     * Ghi đã qua {@code floor} — nguyên tử, chỉ khi đó là tầng mới (best_floor &lt; floor).
     *
     * @return thưởng mốc nếu lần gọi NÀY là lần đầu qua tầng đó và tầng đó có thưởng
     */
    Optional<Reward> recordCleared(long characterId, int floor);

    /** {@code limit} người leo cao nhất. */
    List<Climber> top(int limit);
}
