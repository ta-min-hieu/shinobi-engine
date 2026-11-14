package dev.kage.server.gateway;

import java.time.Instant;
import java.time.LocalDate;

import dev.kage.character.domain.PlayerLookupPort;
import dev.kage.mail.domain.MailArchivePort;
import dev.kage.protocol.ArchiveMailCommand;
import dev.kage.protocol.ArchiveMailOutcome;
import dev.kage.protocol.ArchiveMailResponse;
import dev.kage.protocol.ArchivedMail;
import dev.kage.protocol.GetReferralRankingResponse;
import dev.kage.protocol.GetStreakRankingResponse;
import dev.kage.protocol.ListArchivedMailResponse;
import dev.kage.protocol.PlayerHit;
import dev.kage.protocol.ReferralRank;
import dev.kage.protocol.SearchPlayersRequest;
import dev.kage.protocol.SearchPlayersResponse;
import dev.kage.protocol.StreakRank;
import dev.kage.quest.domain.DailyReset;

/** Lớp lệnh lô 53 (ADR-351…) — MỘT thành phần của {@link Lot52Commands}. Gọi trên Virtual Thread. */
record Lot53Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** Lớp lệnh lô 54 (ADR-359…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot54Commands lot54() {
        return new Lot54Commands(gateway, registry);
    }

    /** ADR-351 — top 10 chuỗi đăng nhập còn sống. */
    GetStreakRankingResponse streakRanking() {
        GetStreakRankingResponse.Builder response = GetStreakRankingResponse.newBuilder();
        LocalDate today = LocalDate.ofInstant(Instant.now(), DailyReset.ZONE);
        for (PlayerLookupPort.Ranked ranked : facades().lookup().topStreaks(today)) {
            response.addEntries(StreakRank.newBuilder().setName(ranked.name()).setStreak(ranked.value()));
        }
        return response.build();
    }

    /** ADR-352 — top 10 người giới thiệu. */
    GetReferralRankingResponse referralRanking() {
        GetReferralRankingResponse.Builder response = GetReferralRankingResponse.newBuilder();
        for (PlayerLookupPort.Ranked ranked : facades().lookup().topReferrers()) {
            response.addEntries(ReferralRank.newBuilder().setName(ranked.name()).setCount(ranked.value()));
        }
        return response.build();
    }

    /** ADR-353 — tìm người chơi theo chuỗi con của tên. */
    SearchPlayersResponse searchPlayers(SearchPlayersRequest request) {
        SearchPlayersResponse.Builder response = SearchPlayersResponse.newBuilder();
        for (PlayerLookupPort.Hit hit : facades().lookup().search(request.getQuery())) {
            response.addPlayers(PlayerHit.newBuilder()
                    .setName(hit.name())
                    .setLevel(hit.level())
                    .setOnline(registry.isOnline(hit.characterId())));
        }
        return response.build();
    }

    /** ADR-354 — cất thư vào kho lưu trữ hoặc trả về hộp chính. */
    ArchiveMailResponse archiveMail(long characterId, ArchiveMailCommand command) {
        boolean done = facades().mailArchive().setArchived(command.getMailId(), characterId, command.getArchive());
        ArchiveMailOutcome outcome;
        if (!done) {
            outcome = ArchiveMailOutcome.ARCHIVE_MAIL_OUTCOME_NOT_FOUND;
        } else {
            outcome = command.getArchive()
                    ? ArchiveMailOutcome.ARCHIVE_MAIL_OUTCOME_ARCHIVED
                    : ArchiveMailOutcome.ARCHIVE_MAIL_OUTCOME_RESTORED;
        }
        return ArchiveMailResponse.newBuilder()
                .setOutcome(outcome)
                .setMailId(command.getMailId())
                .build();
    }

    /** ADR-354 — kho thư lưu trữ, mới trước. */
    ListArchivedMailResponse listArchived(long characterId) {
        ListArchivedMailResponse.Builder response = ListArchivedMailResponse.newBuilder();
        for (MailArchivePort.Entry entry : facades().mailArchive().archived(characterId)) {
            response.addMails(ArchivedMail.newBuilder()
                    .setMailId(entry.mailId())
                    .setSenderName(entry.senderName())
                    .setSubject(entry.subject())
                    .setSentAtEpochSeconds(entry.sentAt().getEpochSecond()));
        }
        return response.build();
    }

    private Lot53Facades facades() {
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
                .lot53();
    }
}
