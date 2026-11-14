package dev.kage.server.gateway;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.NewPlayersFacade;
import dev.kage.character.domain.Character;
import dev.kage.checkin.api.OnlineStatsFacade;
import dev.kage.checkin.domain.OnlineStatsPort;
import dev.kage.friend.domain.IntimacyRankingPort;
import dev.kage.guild.api.GuildFacade;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.DiligenceRank;
import dev.kage.protocol.GetDiligenceRankingResponse;
import dev.kage.protocol.GetIntimacyRankingResponse;
import dev.kage.protocol.GetOnlineWeekResponse;
import dev.kage.protocol.IntimacyPair;
import dev.kage.protocol.OnlineDay;
import dev.kage.protocol.Opcode;

/** Lớp lệnh lô 63 (ADR-431…) — MỘT thành phần của {@link Lot62Commands}. Gọi trên Virtual Thread. */
record Lot63Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    private static final Logger LOG = LoggerFactory.getLogger(Lot63Commands.class);

    /** Lớp lệnh lô 64 (ADR-439…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot64Commands lot64() {
        return new Lot64Commands(gateway, registry);
    }

    /** ADR-431 — top 10 giây online đã lưu của hôm nay (giờ Việt Nam). */
    GetDiligenceRankingResponse diligenceRanking() {
        LocalDate today = LocalDate.ofInstant(Instant.now(), NewPlayersFacade.DAY_ZONE);
        GetDiligenceRankingResponse.Builder response = GetDiligenceRankingResponse.newBuilder();
        for (OnlineStatsPort.Ranked ranked : facades().onlineStats().topOfDay(today)) {
            response.addEntries(
                    DiligenceRank.newBuilder().setName(ranked.name()).setSeconds(ranked.seconds()));
        }
        return response.build();
    }

    /** ADR-432 — 7 ngày gần nhất của chính mình, cũ trước; hôm nay cộng phần phiên đang chạy. */
    GetOnlineWeekResponse onlineWeek(long characterId) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.ofInstant(now, NewPlayersFacade.DAY_ZONE);
        Map<LocalDate, Integer> stored = facades().onlineStats().lastDays(characterId, today);
        int live = liveSecondsToday(registry.onlineSessions.enteredAt(characterId, now), now);
        GetOnlineWeekResponse.Builder response = GetOnlineWeekResponse.newBuilder();
        for (int back = OnlineStatsFacade.DAYS - 1; back >= 0; back--) {
            LocalDate day = today.minusDays(back);
            int seconds = stored.getOrDefault(day, 0) + (back == 0 ? live : 0);
            response.addDays(
                    OnlineDay.newBuilder().setDayEpochDays(day.toEpochDay()).setSeconds(seconds));
        }
        return response.build();
    }

    /**
     * Phần của phiên đang chạy thuộc hôm nay (giờ Việt Nam; phiên vắt qua nửa đêm chỉ tính sau nửa đêm). Tĩnh để test.
     */
    static int liveSecondsToday(Instant enteredAt, Instant now) {
        Instant start = NewPlayersFacade.startOfDay(now);
        Instant from = enteredAt.isAfter(start) ? enteredAt : start;
        return (int) Math.max(0L, Duration.between(from, now).toSeconds());
    }

    /** ADR-434 — top 10 cặp bạn thân mật nhất. */
    GetIntimacyRankingResponse intimacyRanking() {
        GetIntimacyRankingResponse.Builder response = GetIntimacyRankingResponse.newBuilder();
        for (IntimacyRankingPort.Pair pair : facades().intimacy().top()) {
            response.addPairs(IntimacyPair.newBuilder()
                    .setFirstName(pair.first())
                    .setSecondName(pair.second())
                    .setIntimacy(pair.intimacy()));
        }
        return response.build();
    }

    /**
     * ADR-433 — lên cấp mở cảnh giới mới ⇒ mọi đồng môn đang online (gồm chính mình) thấy một dòng chúc mừng trên kênh
     * bang. Việc phụ — lỗi dừng ở đây.
     */
    // PMD AvoidCatchingGenericException: việc phụ khi lên cấp.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onLevelReached(long characterId, int level) {
        if (BreakthroughHooks.realmOpenedAt(level).isEmpty()) {
            return;
        }
        try {
            GuildFacade guilds = gateway.contentFacades().socialFacades().guildFacade();
            if (guilds.findMyGuild(characterId).isEmpty()) {
                return;
            }
            String name = gateway.characterFacade()
                    .findById(characterId)
                    .map(Character::name)
                    .orElse("?");
            byte[] line = breakthroughLine(name, level);
            for (GuildFacade.MemberView member : guilds.listMembers(characterId)) {
                registry.sendToCharacterIfOnline(member.characterId(), Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, line);
            }
        } catch (RuntimeException e) {
            LOG.warn("Không báo được bang đột phá: characterId={}, level={}", characterId, level, e);
        }
    }

    /** Dòng chúc mừng trên kênh bang (không người gửi). Tĩnh để test. */
    static byte[] breakthroughLine(String name, int level) {
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_GUILD)
                .setSenderName("")
                .setBody("Đồng môn " + name + " vừa đột phá cảnh giới mới (cấp " + level + ")! / Guildmate " + name
                        + " broke through to a new realm (level " + level + ")!")
                .build()
                .toByteArray();
    }

    private Lot63Facades facades() {
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
                .lot56()
                .lot57()
                .lot58()
                .lot59()
                .lot61()
                .lot62()
                .lot63();
    }
}
