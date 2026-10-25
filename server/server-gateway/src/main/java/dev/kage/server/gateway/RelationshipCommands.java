package dev.kage.server.gateway;

import dev.kage.character.api.CharacterFacade;

/**
 * Các lớp lệnh lô 21 phần 1 của MỘT kết nối — chặn (ADR-091), sư đồ (ADR-093), đạo lữ (ADR-094), biểu cảm (ADR-092)
 * và gói đọc quan hệ — dựng một lần để constructor {@code GatewayChannelHandler} không vượt trần {@code MethodLength}.
 */
record RelationshipCommands(
        BlockCommands blocks,
        MentorshipCommands mentorship,
        DaoLuCommands daoLu,
        EmoteCommands emotes,
        RelationshipQuery query,
        SwornCommands sworn) {

    /** Nhóm kết nghĩa của một người (gồm người đó) — cho kênh chat kết nghĩa (ADR-110). */
    java.util.List<Long> swornMembersOf(long characterId) {
        return query.swornMembersOf(characterId);
    }

    static RelationshipCommands of(GatewayFacades facades, CharacterFacade characters, WorldGateway worldGateway) {
        RelationshipFacades relationships = facades.progression().relationships();
        BlockCommands blocks = new BlockCommands(relationships.blocks(), relationships.daoLu(), characters);
        PlayerNotices notices =
                new PlayerNotices(facades.contentFacades().socialFacades().mailFacade(), worldGateway.registry());
        return new RelationshipCommands(
                blocks,
                new MentorshipCommands(relationships.mentorship(), characters, blocks, notices),
                new DaoLuCommands(relationships.daoLu(), characters, blocks, notices),
                new EmoteCommands(worldGateway.registry(), System::currentTimeMillis),
                new RelationshipQuery(relationships, characters, worldGateway.registry()::isOnline),
                new SwornCommands(relationships.sworn(), characters, blocks, notices));
    }
}
