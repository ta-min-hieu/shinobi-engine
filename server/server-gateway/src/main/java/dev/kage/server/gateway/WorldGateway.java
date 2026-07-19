package dev.kage.server.gateway;

import dev.kage.server.world.WorldFacade;

/**
 * Gộp {@link WorldFacade} + {@link ConnectedCharacterRegistry} + {@link CharacterPositionPersister}
 * thành 1 tham số — 3 object này LUÔN đi cùng nhau (mọi nơi cần gọi world đều cần cả 3), gộp lại
 * để {@code GatewayServer}/{@code GatewayChannelHandler} không vượt quá 4 tham số constructor
 * (STATIC-ANALYSIS.md).
 */
record WorldGateway(
        WorldFacade facade, ConnectedCharacterRegistry registry, CharacterPositionPersister positionPersister) {
}
