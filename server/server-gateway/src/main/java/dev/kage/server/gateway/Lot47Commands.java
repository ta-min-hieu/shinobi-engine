package dev.kage.server.gateway;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import dev.kage.character.domain.ProfileLikePort;
import dev.kage.monster.domain.BossKillLogPort;
import dev.kage.protocol.BossDamageRank;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.GetBossDamageRankingResponse;
import dev.kage.protocol.GetLikeRankingResponse;
import dev.kage.protocol.LikeRank;
import dev.kage.protocol.Opcode;

/** Lớp lệnh lô 47 (ADR-303…) — MỘT thành phần của {@link Lot46Commands}. Gọi trên Virtual Thread. */
record Lot47Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

    /** ADR-303 — top 10 tổng sát thương boss thế giới. */
    GetBossDamageRankingResponse bossDamageRanking() {
        GetBossDamageRankingResponse.Builder response = GetBossDamageRankingResponse.newBuilder();
        for (BossKillLogPort.Ranked ranked : lot46().bossKills().topWorldDamage()) {
            response.addEntries(BossDamageRank.newBuilder()
                    .setName(ranked.name())
                    .setTotalDamage(ranked.totalDamage())
                    .setKills(ranked.kills()));
        }
        return response.build();
    }

    /** ADR-305 — top 10 lượt thích hồ sơ. */
    GetLikeRankingResponse likeRanking() {
        GetLikeRankingResponse.Builder response = GetLikeRankingResponse.newBuilder();
        for (ProfileLikePort.Ranked ranked : lot46().profileLikes().top()) {
            response.addEntries(LikeRank.newBuilder().setName(ranked.name()).setLikes(ranked.likes()));
        }
        return response.build();
    }

    /**
     * ADR-304 — vào world: gửi lại các tin bang đến SAU lần cuối người này rời world (chưa từng rời ⇒ không gửi gì).
     * Mỗi tin đi đúng kênh bang, tên người gửi giữ nguyên, nội dung kèm giờ (UTC).
     */
    void onEnteredWorld(long characterId) {
        Map<Long, Instant> lastLeft =
                gateway.progression().expansion().lot33().lastOnline().lastOnlineOf(List.of(characterId));
        Instant after = lastLeft.get(characterId);
        if (after == null) {
            return;
        }
        gateway.contentFacades()
                .socialFacades()
                .guildFacade()
                .findMyGuild(characterId)
                .ifPresent(guild -> {
                    for (GuildChatBacklog.Line line : registry.guildChat.since(guild.guildId(), after)) {
                        registry.sendToCharacterIfOnline(
                                characterId, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, replay(line));
                    }
                });
    }

    /** Tin bang phát lại, nội dung kèm giờ gửi. Tĩnh để test. */
    static byte[] replay(GuildChatBacklog.Line line) {
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_GUILD)
                .setSenderName(line.sender())
                .setBody("[" + TIME.format(line.at()) + " UTC] " + line.body())
                .build()
                .toByteArray();
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
