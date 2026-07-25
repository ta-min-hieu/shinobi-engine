package dev.kage.server.gateway;

import dev.kage.server.world.WorldFacade;

/**
 * Gộp {@link WorldFacade} (world chính) + {@link ConnectedCharacterRegistry} +
 * {@link CharacterPositionPersister} + {@link DungeonInstanceRegistry} thành 1 tham số — 4 object
 * này LUÔN đi cùng nhau (mọi nơi cần gọi world đều cần cả 4), gộp lại để
 * {@code GatewayServer}/{@code GatewayChannelHandler} không vượt quá 4 tham số constructor
 * (STATIC-ANALYSIS.md).
 */
record WorldGateway(
        WorldFacade facade, ConnectedCharacterRegistry registry, CharacterPositionPersister positionPersister,
        DungeonInstanceRegistry dungeonInstanceRegistry) {
}
