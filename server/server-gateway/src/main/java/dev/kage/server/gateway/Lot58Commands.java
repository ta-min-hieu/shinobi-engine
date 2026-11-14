package dev.kage.server.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.kage.character.domain.LifetimeRankingPort;
import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.domain.GuildDirectoryPort;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.GetGuildSeniorityResponse;
import dev.kage.protocol.GetLifetimeRankingRequest;
import dev.kage.protocol.GetLifetimeRankingResponse;
import dev.kage.protocol.GuildSeniorityEntry;
import dev.kage.protocol.LifetimeRank;
import dev.kage.protocol.LifetimeRankingKind;

/** Lớp lệnh lô 58 (ADR-391…) — MỘT thành phần của {@link Lot57Commands}. Gọi trên Virtual Thread. */
record Lot58Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** ADR-394 — vắng ít nhất ngần này mới có quà quay lại. */
    static final Duration COMEBACK_ABSENCE = Duration.ofDays(7);

    /** ADR-394 — linh thạch của quà quay lại. */
    static final long COMEBACK_GOLD = 500L;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Lot58Commands.class);

    private static final String COMEBACK_SUBJECT = "Chào mừng trở lại / Welcome back";
    private static final String COMEBACK_BODY = "Đạo hữu đã vắng bóng một thời gian. Tông môn gửi chút linh thạch"
            + " làm lộ phí tu luyện. / You have been away a while — here are some spirit stones to get going again.";

    /** Lớp lệnh lô 59 (ADR-399…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot59Commands lot59() {
        return new Lot59Commands(gateway, registry);
    }

    /** ADR-391/392 — top 10 sát thủ PvP hoặc thời gian tu luyện (mặc định: sát thủ). */
    GetLifetimeRankingResponse lifetimeRanking(GetLifetimeRankingRequest request) {
        LifetimeRankingKind kind = request.getKind() == LifetimeRankingKind.LIFETIME_RANKING_KIND_PLAYTIME
                ? LifetimeRankingKind.LIFETIME_RANKING_KIND_PLAYTIME
                : LifetimeRankingKind.LIFETIME_RANKING_KIND_PLAYER_KILLS;
        List<LifetimeRankingPort.Ranked> ranked = kind == LifetimeRankingKind.LIFETIME_RANKING_KIND_PLAYTIME
                ? facades().lifetimeRanking().topPlaytime()
                : facades().lifetimeRanking().topPlayerKills();
        GetLifetimeRankingResponse.Builder response =
                GetLifetimeRankingResponse.newBuilder().setKind(kind);
        for (LifetimeRankingPort.Ranked entry : ranked) {
            response.addEntries(LifetimeRank.newBuilder().setName(entry.name()).setValue(entry.value()));
        }
        return response.build();
    }

    /** ADR-393 — thành viên bang của mình theo thâm niên, vào sớm nhất trước. */
    GetGuildSeniorityResponse guildSeniority(long characterId) {
        Optional<GuildFacade.MyGuildView> mine =
                gateway.contentFacades().socialFacades().guildFacade().findMyGuild(characterId);
        if (mine.isEmpty()) {
            return GetGuildSeniorityResponse.newBuilder().setInGuild(false).build();
        }
        GetGuildSeniorityResponse.Builder response =
                GetGuildSeniorityResponse.newBuilder().setInGuild(true);
        for (GuildDirectoryPort.Senior senior :
                facades().guildDirectory().seniority(mine.get().guildId())) {
            response.addEntries(GuildSeniorityEntry.newBuilder()
                    .setName(senior.name())
                    .setJoinedEpochSeconds(senior.joinedAt().getEpochSecond()));
        }
        return response.build();
    }

    /**
     * ADR-394 — vào world sau khi vắng ít nhất {@link #COMEBACK_ABSENCE} ⇒ thư hệ thống kèm linh thạch. Khoá chống
     * trùng theo (nhân vật, mốc rời world lần trước) nên mỗi lần vắng chỉ một quà dù vào world lại bao nhiêu lần.
     */
    // PMD AvoidCatchingGenericException: việc phụ của lần vào world — lỗi ở đây không được làm hỏng chọn nhân vật.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onEnteredWorld(long characterId, Instant lastLeft, Instant now) {
        if (!deservesComeback(lastLeft, now)) {
            return;
        }
        try {
            gateway.contentFacades()
                    .socialFacades()
                    .mailFacade()
                    .sendMail(
                            new MailFacade.SendMailRequest(
                                    null, characterId, COMEBACK_SUBJECT, COMEBACK_BODY, COMEBACK_GOLD, List.of()),
                            comebackKey(characterId, lastLeft));
        } catch (RuntimeException e) {
            LOG.warn("Gửi quà quay lại lỗi: characterId={}", characterId, e);
        }
    }

    /** Khoá chống trùng (cột kiểu UUID): UUID tên theo (nhân vật, mốc rời world). Tĩnh để test. */
    static String comebackKey(long characterId, Instant lastLeft) {
        return java.util
                .UUID
                .nameUUIDFromBytes(("comeback:" + characterId + ":" + lastLeft.getEpochSecond())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString();
    }

    /** Vắng đủ lâu chưa. Tĩnh để test. */
    static boolean deservesComeback(Instant lastLeft, Instant now) {
        return !Duration.between(lastLeft, now).minus(COMEBACK_ABSENCE).isNegative();
    }

    private Lot58Facades facades() {
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
                .lot58();
    }
}
