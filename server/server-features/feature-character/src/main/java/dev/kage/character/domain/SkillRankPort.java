package dev.kage.character.domain;

import java.util.HashMap;
import java.util.Map;

/** Bậc chiêu đã nâng của nhân vật (ADR-071) — hiện thực ở server-database ({@code character_skill_rank}). */
public interface SkillRankPort {

    /** Bậc theo mã chiêu; chiêu chưa nâng không có trong map (= bậc 1). Đọc không khoá. */
    Map<String, Integer> ranksOf(long characterId);

    /** Như {@link #ranksOf} nhưng khoá dòng nhân vật tới hết transaction — hai lần nâng song song xếp hàng. */
    Map<String, Integer> lockRanks(Transaction tx, long characterId);

    void setRank(Transaction tx, long characterId, String skillCode, int rank);

    /** Bản trong bộ nhớ — cho composition test. */
    static SkillRankPort inMemory() {
        Map<Long, Map<String, Integer>> byCharacter = new HashMap<>();
        return new SkillRankPort() {
            @Override
            public synchronized Map<String, Integer> ranksOf(long characterId) {
                return Map.copyOf(byCharacter.getOrDefault(characterId, Map.of()));
            }

            @Override
            public Map<String, Integer> lockRanks(Transaction tx, long characterId) {
                return ranksOf(characterId);
            }

            @Override
            public synchronized void setRank(Transaction tx, long characterId, String skillCode, int rank) {
                byCharacter.computeIfAbsent(characterId, id -> new HashMap<>()).put(skillCode, rank);
            }
        };
    }
}
