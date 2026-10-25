package dev.kage.server.gateway;

import java.util.Optional;
import java.util.function.LongPredicate;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.protocol.GetRelationshipsResponse;
import dev.kage.protocol.RelationMemberView;

/**
 * Mọi quan hệ lô 21 của một nhân vật trong MỘT gói (1122/1123): sư phụ, đồ đệ (ADR-093), đạo lữ (ADR-094), danh sách
 * chặn (ADR-091). Chỉ đọc, chạy trên Virtual Thread. Tên/cấp đọc từng người (N+1) — trần mỗi danh sách nhỏ (3 đồ đệ,
 * 50 người chặn) và chỉ chạy khi mở cửa sổ, cùng chấp nhận của danh sách bạn bè.
 */
final class RelationshipQuery {

    private final RelationshipFacades relationships;
    private final CharacterFacade characters;
    private final LongPredicate isOnline;

    RelationshipQuery(RelationshipFacades relationships, CharacterFacade characters, LongPredicate isOnline) {
        this.relationships = relationships;
        this.characters = characters;
        this.isOnline = isOnline;
    }

    GetRelationshipsResponse of(long characterId) {
        GetRelationshipsResponse.Builder response = GetRelationshipsResponse.newBuilder();
        relationships.mentorship().mentorOf(characterId).flatMap(this::view).ifPresent(response::setMentor);
        for (long discipleId : relationships.mentorship().disciplesOf(characterId)) {
            view(discipleId).ifPresent(response::addDisciples);
        }
        relationships.daoLu().partnerOf(characterId).flatMap(this::view).ifPresent(response::setDaoLu);
        for (long blockedId : relationships.blocks().blockedBy(characterId)) {
            view(blockedId).ifPresent(response::addBlocked);
        }
        for (long brotherId : relationships.sworn().membersOf(characterId)) {
            if (brotherId != characterId) {
                view(brotherId).ifPresent(response::addSworn);
            }
        }
        return response.build();
    }

    /** Nhóm kết nghĩa (gồm chính người hỏi) — cho kênh chat kết nghĩa. */
    java.util.List<Long> swornMembersOf(long characterId) {
        return relationships.sworn().membersOf(characterId);
    }

    private Optional<RelationMemberView> view(long characterId) {
        return characters.findById(characterId).map(this::view);
    }

    private RelationMemberView view(Character character) {
        return RelationMemberView.newBuilder()
                .setCharacterId(character.id())
                .setName(character.name())
                .setLevel(character.level())
                .setOnline(isOnline.test(character.id()))
                .build();
    }
}
