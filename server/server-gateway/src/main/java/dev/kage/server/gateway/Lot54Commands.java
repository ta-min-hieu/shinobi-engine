package dev.kage.server.gateway;

import java.time.Instant;

import dev.kage.character.api.ProfileVisitFacade;
import dev.kage.character.domain.ProfileVisitPort;
import dev.kage.protocol.GetInspectHistoryResponse;
import dev.kage.protocol.GetProfileLikersResponse;
import dev.kage.protocol.InspectCharacterOutcome;
import dev.kage.protocol.InspectCharacterResponse;
import dev.kage.protocol.InspectVisit;
import dev.kage.protocol.MarkAllMailReadResponse;
import dev.kage.protocol.ProfileLiker;
import dev.kage.protocol.SetInspectFriendsOnlyCommand;
import dev.kage.protocol.SetInspectFriendsOnlyResponse;

/** Lớp lệnh lô 54 (ADR-359…) — MỘT thành phần của {@link Lot53Commands}. Gọi trên Virtual Thread. */
record Lot54Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** ADR-359 — mọi thư chưa đọc thành đã đọc. */
    MarkAllMailReadResponse markAllMailRead(long characterId) {
        int marked = gateway.progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .lot38()
                .lot39()
                .lot40()
                .lot41()
                .lot42()
                .lot44()
                .lot45()
                .lot46()
                .lot48()
                .mailRead()
                .markAllRead(characterId);
        return MarkAllMailReadResponse.newBuilder().setMarked(marked).build();
    }

    /** ADR-360 — người đã thích hồ sơ mình gần nhất, kèm tổng lượt. */
    GetProfileLikersResponse profileLikers(long characterId) {
        GetProfileLikersResponse.Builder response = GetProfileLikersResponse.newBuilder()
                .setTotal(gateway.progression()
                        .expansion()
                        .lot34()
                        .lot35()
                        .lot36()
                        .lot37()
                        .lot38()
                        .lot39()
                        .lot40()
                        .lot41()
                        .lot42()
                        .lot44()
                        .lot45()
                        .lot46()
                        .profileLikes()
                        .likesOf(characterId));
        for (ProfileVisitPort.Liker liker : visits().recentLikers(characterId)) {
            response.addLikers(ProfileLiker.newBuilder()
                    .setName(liker.name())
                    .setLikedOnEpochDay(ProfileVisitFacade.epochDay(liker.lastLikedOn())));
        }
        return response.build();
    }

    /** ADR-361 — mười lần bị soi gần nhất. */
    GetInspectHistoryResponse inspectHistory(long characterId) {
        GetInspectHistoryResponse.Builder response = GetInspectHistoryResponse.newBuilder();
        for (ProfileVisitPort.Visit visit : visits().inspectHistory(characterId)) {
            response.addVisits(InspectVisit.newBuilder()
                    .setViewerName(visit.viewerName())
                    .setAtEpochSeconds(visit.at().getEpochSecond()));
        }
        return response.build();
    }

    /** ADR-362 — hỏi / bật / tắt "chỉ bạn bè được soi". */
    SetInspectFriendsOnlyResponse setInspectFriendsOnly(long characterId, SetInspectFriendsOnlyCommand command) {
        if (!command.getQueryOnly()) {
            visits().setFriendsOnly(characterId, command.getEnabled());
        }
        return SetInspectFriendsOnlyResponse.newBuilder()
                .setEnabled(visits().friendsOnly(characterId))
                .build();
    }

    /**
     * Soi thành công người KHÁC: ADR-362 — người đó chỉ cho bạn bè soi mà mình không phải bạn ⇒ HIDDEN (chỉ còn tên);
     * ngược lại ADR-361 ghi một lần bị soi. Tự soi mình hoặc kết quả không OK ⇒ trả nguyên.
     */
    InspectCharacterResponse guardInspect(long viewerId, InspectCharacterResponse inspected) {
        if (inspected.getOutcome() != InspectCharacterOutcome.INSPECT_CHARACTER_OUTCOME_OK) {
            return inspected;
        }
        return gateway.characterFacade()
                .findByName(inspected.getName())
                .filter(target -> target.id() != viewerId)
                .map(target -> {
                    if (visits().friendsOnly(target.id()) && !isFriend(target.id(), viewerId)) {
                        return InspectCharacterResponse.newBuilder()
                                .setOutcome(InspectCharacterOutcome.INSPECT_CHARACTER_OUTCOME_HIDDEN)
                                .setName(target.name())
                                .build();
                    }
                    visits().recordInspect(target.id(), viewerId, Instant.now());
                    return inspected;
                })
                .orElse(inspected);
    }

    private boolean isFriend(long characterId, long otherId) {
        return gateway.contentFacades()
                .socialFacades()
                .friendFacade()
                .listFriends(characterId)
                .contains(otherId);
    }

    private ProfileVisitFacade visits() {
        return gateway.progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .lot38()
                .lot39()
                .lot40()
                .lot41()
                .lot42()
                .lot44()
                .lot45()
                .lot46()
                .lot48()
                .lot49()
                .lot50()
                .lot51()
                .lot52()
                .lot53()
                .lot54()
                .profileVisits();
    }
}
