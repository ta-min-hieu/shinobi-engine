package dev.kage.server.gateway;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import dev.kage.character.domain.Character;
import dev.kage.character.domain.ProfileLikePort;
import dev.kage.friend.api.InvisibleModeFacade;
import dev.kage.guild.api.GuildFacade;
import dev.kage.protocol.FriendOfflineEvent;
import dev.kage.protocol.FriendOnlineEvent;
import dev.kage.protocol.GetGuildLevelRankingResponse;
import dev.kage.protocol.GetLikeRankingRequest;
import dev.kage.protocol.GetLikeRankingResponse;
import dev.kage.protocol.GuildLevelRank;
import dev.kage.protocol.LikeRank;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetInvisibleCommand;
import dev.kage.protocol.SetInvisibleResponse;

/** Lớp lệnh lô 57 (ADR-383…) — MỘT thành phần của {@link Lot56Commands}. Gọi trên Virtual Thread. */
record Lot57Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** ADR-383 — bật/tắt (hoặc hỏi) chế độ ẩn; đổi trạng thái khi đang online thì báo bạn bè như vào/thoát world. */
    SetInvisibleResponse setInvisible(long characterId, SetInvisibleCommand command) {
        boolean before = invisible().isInvisible(characterId);
        if (command.getQueryOnly() || command.getEnabled() == before) {
            return SetInvisibleResponse.newBuilder().setEnabled(before).build();
        }
        invisible().setInvisible(characterId, command.getEnabled());
        if (registry.isOnline(characterId)) {
            announce(characterId, !command.getEnabled());
        }
        return SetInvisibleResponse.newBuilder()
                .setEnabled(command.getEnabled())
                .build();
    }

    /** ADR-383 — nhân vật có đang ẩn với bạn bè không (chạm DB). */
    boolean isInvisible(long characterId) {
        return invisible().isInvisible(characterId);
    }

    /** ADR-383 — trạng thái online bạn bè được thấy: online thật VÀ không ẩn. */
    boolean shownOnline(long characterId) {
        return registry.isOnline(characterId) && !isInvisible(characterId);
    }

    /** ADR-384 — xếp hạng lượt thích: cả đời (ADR-305) hoặc 7 ngày gần nhất. */
    GetLikeRankingResponse likeRanking(GetLikeRankingRequest request, GetLikeRankingResponse allTime) {
        if (!request.getWeekly()) {
            return allTime;
        }
        GetLikeRankingResponse.Builder response =
                GetLikeRankingResponse.newBuilder().setWeekly(true);
        for (ProfileLikePort.Ranked ranked : lot46().profileLikes().topThisWeek()) {
            response.addEntries(LikeRank.newBuilder().setName(ranked.name()).setLikes(ranked.likes()));
        }
        return response.build();
    }

    /** ADR-385 — mọi thành viên bang của mình theo cấp, cao trước (cùng cấp xếp theo tên). */
    GetGuildLevelRankingResponse guildLevelRanking(long characterId) {
        GuildFacade guilds = gateway.contentFacades().socialFacades().guildFacade();
        if (guilds.findMyGuild(characterId).isEmpty()) {
            return GetGuildLevelRankingResponse.newBuilder().setInGuild(false).build();
        }
        List<GuildLevelRank> ranks = new ArrayList<>();
        for (GuildFacade.MemberView member : guilds.listMembers(characterId)) {
            Optional<Character> found = gateway.characterFacade().findById(member.characterId());
            found.ifPresent(character -> ranks.add(GuildLevelRank.newBuilder()
                    .setName(character.name())
                    .setLevel(character.level())
                    .setOnline(registry.isOnline(character.id()))
                    .build()));
        }
        ranks.sort(LEVEL_ORDER);
        return GetGuildLevelRankingResponse.newBuilder()
                .setInGuild(true)
                .addAllEntries(ranks)
                .build();
    }

    /** Cấp cao trước, cùng cấp theo tên. Tĩnh để test. */
    static final Comparator<GuildLevelRank> LEVEL_ORDER =
            Comparator.comparingInt(GuildLevelRank::getLevel).reversed().thenComparing(GuildLevelRank::getName);

    private void announce(long characterId, boolean online) {
        int opcode = online ? Opcode.OPCODE_FRIEND_ONLINE_EVENT_VALUE : Opcode.OPCODE_FRIEND_OFFLINE_EVENT_VALUE;
        byte[] payload = online
                ? FriendOnlineEvent.newBuilder()
                        .setCharacterId(characterId)
                        .build()
                        .toByteArray()
                : FriendOfflineEvent.newBuilder()
                        .setCharacterId(characterId)
                        .build()
                        .toByteArray();
        for (long friendId :
                gateway.contentFacades().socialFacades().friendFacade().listFriends(characterId)) {
            registry.sendToCharacterIfOnline(friendId, opcode, 0, payload);
        }
    }

    private InvisibleModeFacade invisible() {
        return lot46().lot48()
                .lot49()
                .lot50()
                .lot51()
                .lot52()
                .lot53()
                .lot54()
                .lot56()
                .lot57()
                .invisible();
    }

    private Lot46Facades lot46() {
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
                .lot46();
    }
}
