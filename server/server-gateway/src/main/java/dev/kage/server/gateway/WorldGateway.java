package dev.kage.server.gateway;

import dev.kage.server.world.WorldFacade;

/**
 * Gộp {@link WorldFacade} (world chính) + {@link ConnectedCharacterRegistry} +
 * {@link CharacterPositionPersister} + {@link DungeonInstanceRegistry} + {@link ArenaInstanceRegistry}
 * thành 1 tham số — các object này LUÔN đi cùng nhau (mọi nơi cần gọi world đều cần cả nhóm), gộp
 * lại để {@code GatewayServer}/{@code GatewayChannelHandler} không vượt quá 4 tham số constructor
 * (STATIC-ANALYSIS.md). Record KHÔNG có compact constructor tường minh nên bản thân nó KHÔNG bị
 * Checkstyle {@code ParameterNumber} tính (tiền lệ {@code SocialFacades}) — thêm
 * {@code arenaInstanceRegistry} (Phase 8 lát cắt 4) không vi phạm giới hạn.
 */
record WorldGateway(
        WorldFacade facade, ConnectedCharacterRegistry registry, CharacterPositionPersister positionPersister,
        DungeonInstanceRegistry dungeonInstanceRegistry, ArenaInstanceRegistry arenaInstanceRegistry) {
}
