package dev.kage.server.gateway;

import dev.kage.checkin.domain.WeeklyTaskRules;

/**
 * Việc phụ CHỈ của người ra đòn cuối sau một lượt hạ quái — truy nã (ADR-101), sổ sưu tầm (ADR-108), kỳ ngộ (ADR-121),
 * nhiệm vụ tuần (ADR-154). Mỗi bên tự nuốt lỗi của nó. Tách khỏi {@link ConnectedCharacterRegistry} vì lớp đó chạm trần
 * CyclomaticComplexity cả lớp.
 */
record KillerSideEffects(
        BountyHooks bounties, CollectionHooks collections, EncounterHooks encounters, ActivityHooks activity) {

    void run(long killerCharacterId, String monsterCode) {
        bounties.onMonsterKilled(killerCharacterId, monsterCode);
        collections.onMonsterKilled(killerCharacterId, monsterCode);
        encounters.onMonsterKilled(killerCharacterId);
        activity.recordWeekly(killerCharacterId, WeeklyTaskRules.MONSTER_KILL);
    }
}
