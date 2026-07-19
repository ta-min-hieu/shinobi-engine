package dev.kage.server.gateway;

import dev.kage.server.world.WorldFacade;

/**
 * Gộp {@link WorldFacade} + {@link ConnectedCharacterRegistry} thành 1 tham số — 2 object này
 * LUÔN đi cùng nhau (mọi nơi cần gọi world đều cần cả 2), gộp lại để {@code GatewayServer}/
 * {@code GatewayChannelHandler} không vượt quá 4 tham số constructor (STATIC-ANALYSIS.md).
 */
record WorldGateway(WorldFacade facade, ConnectedCharacterRegistry registry) {
}
