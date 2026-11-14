package dev.kage.tools.loadtest.probe;

import java.io.IOException;

import dev.kage.protocol.Opcode;

/**
 * Bước lô 53: xếp hạng chuỗi (ADR-351) {@code topStreak}; xếp hạng giới thiệu (ADR-352) {@code topReferral}; tìm người
 * (ADR-353) {@code findPlayer:<chữ>}; lưu trữ thư (ADR-354) {@code archiveMail:<id>|-<id>}, {@code archivedMail}.
 */
final class Lot53Steps {

    private final Lot23Scenario.Asker asker;
    private final Lot54Steps lot54;

    Lot53Steps(Lot23Scenario.Asker asker) {
        this.asker = asker;
        this.lot54 = new Lot54Steps(asker);
    }

    String step(String verb, String arg) throws IOException {
        return switch (verb) {
            case "topStreak" ->
                asker.ask(
                        Opcode.OPCODE_GET_STREAK_RANKING_REQUEST_VALUE,
                        dev.kage.protocol.GetStreakRankingRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_STREAK_RANKING_RESPONSE_VALUE,
                        p -> {
                            StringBuilder out = new StringBuilder("topChuỗi");
                            for (dev.kage.protocol.StreakRank rank :
                                    dev.kage.protocol.GetStreakRankingResponse.parseFrom(p)
                                            .getEntriesList()) {
                                out.append(' ')
                                        .append(rank.getName())
                                        .append('=')
                                        .append(rank.getStreak());
                            }
                            return out.toString();
                        });
            case "topReferral" ->
                asker.ask(
                        Opcode.OPCODE_GET_REFERRAL_RANKING_REQUEST_VALUE,
                        dev.kage.protocol.GetReferralRankingRequest.getDefaultInstance(),
                        Opcode.OPCODE_GET_REFERRAL_RANKING_RESPONSE_VALUE,
                        p -> "topGiớiThiệu="
                                + dev.kage.protocol.GetReferralRankingResponse.parseFrom(p)
                                        .getEntriesCount());
            case "findPlayer" -> findPlayer(arg);
            case "archiveMail" ->
                asker.ask(
                        Opcode.OPCODE_ARCHIVE_MAIL_COMMAND_VALUE,
                        dev.kage.protocol.ArchiveMailCommand.newBuilder()
                                .setMailId(Math.abs(Long.parseLong(arg)))
                                .setArchive(!arg.startsWith("-"))
                                .build(),
                        Opcode.OPCODE_ARCHIVE_MAIL_RESPONSE_VALUE,
                        p -> dev.kage.protocol.ArchiveMailResponse.parseFrom(p)
                                .getOutcome()
                                .name());
            case "archivedMail" ->
                asker.ask(
                        Opcode.OPCODE_LIST_ARCHIVED_MAIL_REQUEST_VALUE,
                        dev.kage.protocol.ListArchivedMailRequest.getDefaultInstance(),
                        Opcode.OPCODE_LIST_ARCHIVED_MAIL_RESPONSE_VALUE,
                        p -> "lưuTrữ="
                                + dev.kage.protocol.ListArchivedMailResponse.parseFrom(p)
                                        .getMailsCount());
            default -> lot54.step(verb, arg);
        };
    }

    private String findPlayer(String query) throws IOException {
        return asker.ask(
                Opcode.OPCODE_SEARCH_PLAYERS_REQUEST_VALUE,
                dev.kage.protocol.SearchPlayersRequest.newBuilder()
                        .setQuery(query)
                        .build(),
                Opcode.OPCODE_SEARCH_PLAYERS_RESPONSE_VALUE,
                p -> {
                    StringBuilder out = new StringBuilder("tìmThấy");
                    for (dev.kage.protocol.PlayerHit hit :
                            dev.kage.protocol.SearchPlayersResponse.parseFrom(p).getPlayersList()) {
                        out.append(' ').append(hit.getName()).append(hit.getOnline() ? "(on)" : "");
                    }
                    return out.toString();
                });
    }
}
