package dev.kage.server.gateway;

import dev.kage.server.world.WorldOutboundSink;

/**
 * {@link WorldOutboundSink} cho duel instance (Phase 8 lát cắt 4) — bọc {@link ConnectedCharacterRegistry}
 * DÙNG CHUNG với world chính, cùng tinh thần {@code DungeonOutboundSink}. Khác 2 chỗ:
 * {@link #onMoveAccepted} (không ghi đè {@code lastKnownPosition}, lý do y hệt dungeon) VÀ
 * {@link #onCharacterDefeated} (route thẳng vào {@link ArenaInstanceRegistry#handleCharacterDefeated}
 * để chốt kết quả trận đấu, thay vì no-op của world chính).
 */
final class ArenaOutboundSink implements WorldOutboundSink {

    private final ConnectedCharacterRegistry registry;
    private final ArenaInstanceRegistry arenaInstanceRegistry;

    ArenaOutboundSink(ConnectedCharacterRegistry registry, ArenaInstanceRegistry arenaInstanceRegistry) {
        this.registry = registry;
        this.arenaInstanceRegistry = arenaInstanceRegistry;
    }

    @Override
    public void onMoveAccepted(WorldOutboundSink.MoveAcceptedSnapshot accepted) {
        registry.sendMoveResponseWithoutTrackingPosition(accepted);
    }

    @Override
    public void onCharacterSpawned(long targetCharacterId, long spawnedCharacterId, float x, float y) {
        registry.onCharacterSpawned(targetCharacterId, spawnedCharacterId, x, y);
    }

    @Override
    public void onCharacterDespawned(long targetCharacterId, long despawnedCharacterId) {
        registry.onCharacterDespawned(targetCharacterId, despawnedCharacterId);
    }

    @Override
    public void onCharacterMoved(long targetCharacterId, long movedCharacterId, float x, float y) {
        registry.onCharacterMoved(targetCharacterId, movedCharacterId, x, y);
    }

    @Override
    public void onMonsterSpawned(long targetCharacterId, WorldOutboundSink.MonsterSnapshot monster) {
        registry.onMonsterSpawned(targetCharacterId, monster);
    }

    @Override
    public void onMonsterDespawned(long targetCharacterId, long monsterId) {
        registry.onMonsterDespawned(targetCharacterId, monsterId);
    }

    @Override
    public void onMonsterMoved(long targetCharacterId, long monsterId, float x, float y) {
        registry.onMonsterMoved(targetCharacterId, monsterId, x, y);
    }

    @Override
    public void onMonsterKilled(long killerCharacterId, String monsterCode, long expReward) {
        registry.onMonsterKilled(killerCharacterId, monsterCode, expReward);
    }

    @Override
    public void onNpcSpawned(long targetCharacterId, WorldOutboundSink.NpcSnapshot npc) {
        registry.onNpcSpawned(targetCharacterId, npc);
    }

    @Override
    public void onNpcDespawned(long targetCharacterId, long npcId) {
        registry.onNpcDespawned(targetCharacterId, npcId);
    }

    @Override
    public void onAttackResult(long targetCharacterId, WorldOutboundSink.AttackOutcome outcome) {
        registry.onAttackResult(targetCharacterId, outcome);
    }

    @Override
    public void onSkillResult(long targetCharacterId, WorldOutboundSink.SkillOutcome outcome) {
        registry.onSkillResult(targetCharacterId, outcome);
    }

    @Override
    public void onAreaChatMessage(long targetCharacterId, long senderCharacterId, String senderName, String body) {
        registry.onAreaChatMessage(targetCharacterId, senderCharacterId, senderName, body);
    }

    @Override
    public void onCharacterAppearanceChanged(long targetCharacterId, long changedCharacterId) {
        registry.onCharacterAppearanceChanged(targetCharacterId, changedCharacterId);
    }

    @Override
    public void onCharacterDefeated(long defeatedCharacterId, long killerEntityId) {
        arenaInstanceRegistry.handleCharacterDefeated(defeatedCharacterId, killerEntityId);
    }

    @Override
    public void onCharacterRespawned(WorldOutboundSink.RespawnSnapshot respawn) {
        registry.onCharacterRespawned(respawn);
    }
}
