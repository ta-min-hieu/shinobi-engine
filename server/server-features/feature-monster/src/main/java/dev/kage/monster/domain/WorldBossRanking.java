package dev.kage.monster.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Xếp hạng sổ sát thương và ghép bậc thưởng (ADR-081 D4) — hàm thuần. */
public final class WorldBossRanking {

    private WorldBossRanking() {}

    /**
     * Một người trong bảng xếp hạng.
     *
     * @param rank hạng từ 1
     * @param characterId nhân vật
     * @param damage tổng sát thương lên con boss
     */
    public record Entry(int rank, long characterId, long damage) {}

    /** Sát thương giảm dần; hoà thì id nhỏ trước (ổn định, không phụ thuộc thứ tự map). Chỉ người gây > 0. */
    public static List<Entry> rank(Map<Long, Long> damageByCharacter) {
        List<Map.Entry<Long, Long>> rows = new ArrayList<>(damageByCharacter.entrySet());
        rows.removeIf(row -> row.getValue() <= 0);
        rows.sort(Comparator.<Map.Entry<Long, Long>>comparingLong(Map.Entry::getValue)
                .reversed()
                .thenComparingLong(Map.Entry::getKey));
        List<Entry> ranked = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            ranked.add(new Entry(i + 1, rows.get(i).getKey(), rows.get(i).getValue()));
        }
        return ranked;
    }

    /** Bậc thưởng đầu tiên phủ {@code rank}; rỗng nếu không bậc nào phủ. */
    public static Optional<WorldBossReward> rewardFor(int rank, List<WorldBossReward> tiers) {
        return tiers.stream()
                .filter(tier -> rank >= tier.rankFrom() && rank <= tier.rankTo())
                .findFirst();
    }
}
