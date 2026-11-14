package dev.kage.server.gateway;

import java.util.List;
import java.util.Optional;

import dev.kage.character.domain.PvpStatsPort;
import dev.kage.chat.domain.ChatMessage;
import dev.kage.chat.domain.ProfanityFilter;
import dev.kage.friend.api.FriendRequestNoteFacade;
import dev.kage.mail.api.MailFacade;
import dev.kage.party.api.PartyFacade;
import dev.kage.protocol.GetPeakOnlineResponse;
import dev.kage.protocol.GetPersonalStatsResponse;
import dev.kage.protocol.NewMailEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendPartyMailCommand;
import dev.kage.protocol.SendPartyMailOutcome;
import dev.kage.protocol.SendPartyMailResponse;

/** Lớp lệnh lô 56 (ADR-375…) — MỘT thành phần của {@link Lot55Commands}. Gọi trên Virtual Thread. */
record Lot56Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** Lớp lệnh lô 57 (ADR-383…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot57Commands lot57() {
        return new Lot57Commands(gateway, registry);
    }

    /** ADR-375 — tiêu đề thư nhóm tối đa (bằng thư thường). */
    static final int MAX_SUBJECT = 64;

    /** ADR-375 — nội dung thư nhóm tối đa. */
    static final int MAX_BODY = 1024;

    /** ADR-375 — gửi một thư (không đính kèm) tới mọi thành viên khác trong nhóm. */
    SendPartyMailResponse sendPartyMail(long characterId, SendPartyMailCommand command) {
        Optional<PartyFacade.MyPartyView> party = parties().findMyParty(characterId);
        if (party.isEmpty()) {
            return partyMail(SendPartyMailOutcome.SEND_PARTY_MAIL_OUTCOME_NOT_IN_PARTY, 0);
        }
        String subject = command.getSubject().strip();
        if (!validPartyMail(subject, command.getBody())) {
            return partyMail(SendPartyMailOutcome.SEND_PARTY_MAIL_OUTCOME_INVALID_CONTENT, 0);
        }
        List<Long> others = parties().listMembers(characterId).stream()
                .map(PartyFacade.MemberView::characterId)
                .filter(id -> id != characterId)
                .toList();
        if (others.isEmpty()) {
            return partyMail(SendPartyMailOutcome.SEND_PARTY_MAIL_OUTCOME_NO_RECIPIENTS, 0);
        }
        int sent = deliverAll(characterId, others, subject, command.getBody());
        return sent == 0
                ? partyMail(SendPartyMailOutcome.SEND_PARTY_MAIL_OUTCOME_INVALID_CONTENT, 0)
                : partyMail(SendPartyMailOutcome.SEND_PARTY_MAIL_OUTCOME_OK, sent);
    }

    /** Tiêu đề 1..{@value #MAX_SUBJECT} ký tự, nội dung ≤ {@value #MAX_BODY}, không từ cấm. Tĩnh để test. */
    static boolean validPartyMail(String subject, String body) {
        return !subject.isEmpty()
                && subject.length() <= MAX_SUBJECT
                && body.length() <= MAX_BODY
                && !ProfanityFilter.containsBannedWord(subject)
                && !ProfanityFilter.containsBannedWord(body);
    }

    /**
     * ADR-376 — làm sạch lời nhắn kèm lời mời: một dòng, bỏ ký tự điều khiển (Postgres từ chối NUL), cắt về 60 ký tự,
     * có từ cấm ⇒ bỏ. Tĩnh để test.
     */
    static String cleanRequestNote(String raw) {
        String line = ChatMessage.singleLine(raw)
                .codePoints()
                .filter(cp -> !java.lang.Character.isISOControl(cp))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
                .strip();
        String cut = line.length() > FriendRequestNoteFacade.MAX_LENGTH
                ? line.substring(0, FriendRequestNoteFacade.MAX_LENGTH)
                : line;
        return ProfanityFilter.containsBannedWord(cut) ? "" : cut;
    }

    /** ADR-376 — lời mời vừa gửi thành công: nhớ lời nhắn kèm theo. */
    void rememberRequestNote(long requesterId, long addresseeId, String raw) {
        notes().save(requesterId, addresseeId, cleanRequestNote(raw));
    }

    /** ADR-376 — lời nhắn của một lời mời đang chờ ("" nếu không có). */
    String requestNote(long requesterId, long addresseeId) {
        return notes().messageOf(requesterId, addresseeId);
    }

    /** ADR-377 — đỉnh online hôm nay + số đang online. */
    GetPeakOnlineResponse peakOnline() {
        int current = registry.onlineCharacterIds().size();
        PeakOnlineTracker.Peak peak = registry.peakOnline.today(current);
        return GetPeakOnlineResponse.newBuilder()
                .setPeak(peak.peak())
                .setPeakAtEpochSeconds(peak.at().getEpochSecond())
                .setCurrent(current)
                .build();
    }

    /** ADR-378 — gắn số lần hạ / bị hạ bởi người chơi vào bảng thống kê cá nhân (ADR-223). */
    GetPersonalStatsResponse withPvpStats(long characterId, GetPersonalStatsResponse base) {
        PvpStatsPort.Stats stats = facades().pvpStats().stats(characterId);
        return base.toBuilder()
                .setPlayerKills(stats.playerKills())
                .setDeathsByPlayer(stats.deathsByPlayer())
                .build();
    }

    private int deliverAll(long senderId, List<Long> recipients, String subject, String body) {
        MailFacade mail = gateway.contentFacades().socialFacades().mailFacade();
        int sent = 0;
        for (long recipient : recipients) {
            MailFacade.SendOutcome outcome =
                    mail.sendMail(new MailFacade.SendMailRequest(senderId, recipient, subject, body, 0L, List.of()));
            if (outcome.result() == MailFacade.SendResult.OK) {
                sent++;
                registry.sendToCharacterIfOnline(
                        recipient,
                        Opcode.OPCODE_NEW_MAIL_EVENT_VALUE,
                        0,
                        NewMailEvent.newBuilder()
                                .setMailId(outcome.mailId())
                                .build()
                                .toByteArray());
            }
        }
        return sent;
    }

    private static SendPartyMailResponse partyMail(SendPartyMailOutcome outcome, int recipients) {
        return SendPartyMailResponse.newBuilder()
                .setOutcome(outcome)
                .setRecipients(recipients)
                .build();
    }

    private PartyFacade parties() {
        return gateway.contentFacades().socialFacades().partyFacade();
    }

    private FriendRequestNoteFacade notes() {
        return facades().requestNotes();
    }

    private Lot56Facades facades() {
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
                .lot56();
    }
}
