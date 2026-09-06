package dev.kage.server.gateway;

import dev.kage.server.world.WorldOutboundSink;

/**
 * {@link WorldOutboundSink} cho dungeon instance (Phase 8 lát cắt 1, ADR-026) — bọc
 * {@link ConnectedCharacterRegistry} DÙNG CHUNG với world chính (gửi packet vẫn tra
 * {@code characterId -> Channel} như nhau, không phụ thuộc nhân vật đang ở World nào). CHỈ khác
 * {@link #onMoveAccepted} — gọi {@link ConnectedCharacterRegistry#sendMoveResponseWithoutTrackingPosition}
 * thay vì {@code onMoveAccepted} thật, để toạ độ dungeon (không gian riêng của instance) không ghi
 * đè {@code lastKnownPosition} (nguồn cho {@code CharacterPositionPersister} lưu vị trí THẬT).
 */
final class DungeonOutboundSink implements WorldOutboundSink {

    private final ConnectedCharacterRegistry registry;

    DungeonOutboundSink(ConnectedCharacterRegistry registry) {
        this.registry = registry;
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
        registry.onCharacterDefeated(defeatedCharacterId, killerEntityId);
    }

    @Override
    public void onCharacterRespawned(WorldOutboundSink.RespawnSnapshot respawn) {
        registry.onCharacterRespawned(respawn);
    }
}
