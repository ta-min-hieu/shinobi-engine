package dev.kage.server.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.character.api.LoginStreakFacade;
import dev.kage.character.domain.Character;
import dev.kage.character.domain.LoginStreakPort;
import dev.kage.mail.api.MailFacade;
import dev.kage.party.domain.RecentPlayerPort;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.GetLoginStreakResponse;
import dev.kage.protocol.GetRecentPlayersResponse;
import dev.kage.protocol.InspectCharacterOutcome;
import dev.kage.protocol.InspectCharacterResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RecentPlayer;
import dev.kage.protocol.SetInspectNoticeCommand;
import dev.kage.protocol.SetInspectNoticeResponse;
import dev.kage.quest.domain.DailyReset;

/** Lớp lệnh lô 50 (ADR-327…) — MỘT thành phần của {@link Lot49Commands}. Gọi trên Virtual Thread. */
record Lot50Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    private static final Logger LOG = LoggerFactory.getLogger(Lot50Commands.class);
    private static final String STREAK_SUBJECT = "Quà chuỗi đăng nhập / Login streak gift";

    /** Lớp lệnh lô 51 (ADR-335…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot51Commands lot51() {
        return new Lot51Commands(gateway, registry);
    }

    /** ADR-327 — chuỗi hiện tại, dài nhất và mốc thưởng kế tiếp. */
    GetLoginStreakResponse loginStreak(long characterId) {
        LoginStreakFacade.Status status = facades().loginStreak().status(characterId, today());
        int next = LoginStreakFacade.nextMilestone(status.current());
        return GetLoginStreakResponse.newBuilder()
                .setCurrent(status.current())
                .setBest(status.best())
                .setNextRewardDay(next)
                .setNextRewardGold(LoginStreakFacade.rewardFor(next))
                .build();
    }

    /** Vào world (Virtual Thread): ADR-327 tính chuỗi đăng nhập; ADR-328 cảnh báo đăng nhập từ vùng mạng lạ. */
    // PMD AvoidCatchingGenericException: việc phụ của vào world — lỗi không được làm hỏng đường vào world.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onEnteredWorld(long characterId) {
        try {
            facades().loginStreak().advance(characterId, today()).ifPresent(row -> onStreakAdvanced(characterId, row));
            gateway.characterFacade()
                    .findById(characterId)
                    .filter(me -> loginHistory().latestIsNewNetwork(me.accountId()))
                    .ifPresent(me -> notice(characterId, newNetworkNotice()));
        } catch (RuntimeException e) {
            LOG.warn("Việc phụ lô 50 khi vào world lỗi: characterId={}", characterId, e);
        }
    }

    private void onStreakAdvanced(long characterId, LoginStreakPort.Row row) {
        long gold = LoginStreakFacade.rewardFor(row.streak());
        notice(characterId, streakNotice(row.streak(), gold));
        if (gold > 0) {
            String body = "Chuỗi đăng nhập " + row.streak() + " ngày. / Login streak: " + row.streak() + " days.";
            gateway.contentFacades()
                    .socialFacades()
                    .mailFacade()
                    .sendMail(
                            new MailFacade.SendMailRequest(null, characterId, STREAK_SUBJECT, body, gold, List.of()),
                            streakKey(characterId, row.lastDay()));
        }
    }

    /** Khoá chống trùng thư quà chuỗi (một thư mỗi nhân vật mỗi ngày). Tĩnh để test. */
    static String streakKey(long characterId, LocalDate day) {
        return UUID.nameUUIDFromBytes(("login-streak:" + characterId + ":" + day).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    /** Dòng báo chuỗi đăng nhập (kèm thưởng nếu là mốc). Tĩnh để test. */
    static byte[] streakNotice(int streak, long gold) {
        String body = "Chuỗi đăng nhập: " + streak + " ngày liên tiếp. / Login streak: " + streak + " days.";
        if (gold > 0) {
            body += " Quà " + gold + " linh thạch đã gửi vào thư. / " + gold + " spirit stones sent by mail.";
        }
        return line(body);
    }

    /** Dòng cảnh báo vùng mạng lạ. Tĩnh để test. */
    static byte[] newNetworkNotice() {
        return line("Cảnh báo: tài khoản vừa đăng nhập từ vùng mạng chưa từng thấy. Nếu không phải bạn, hãy xem "
                + "/lichsudangnhap. / Warning: this account just signed in from a new network. If it was not you, "
                + "check /loginlog.");
    }

    /** ADR-329 — mười người gần nhất từng chung nhóm, mới trước. */
    GetRecentPlayersResponse recentPlayers(long characterId) {
        GetRecentPlayersResponse.Builder response = GetRecentPlayersResponse.newBuilder();
        for (RecentPlayerPort.Entry entry : facades().recentPlayers().recent(characterId)) {
            gateway.characterFacade()
                    .findById(entry.otherId())
                    .ifPresent(other -> response.addPlayers(RecentPlayer.newBuilder()
                            .setName(other.name())
                            .setLevel(other.level())
                            .setOnline(registry.isOnline(other.id()))
                            .setMetAtEpochSeconds(entry.metAt().getEpochSecond())));
        }
        return response.build();
    }

    /** ADR-330 — hỏi / bật / tắt báo khi bị soi trang bị. */
    SetInspectNoticeResponse setInspectNotice(long characterId, SetInspectNoticeCommand command) {
        if (!command.getQueryOnly()) {
            facades().inspectNotice().setEnabled(characterId, command.getEnabled());
        }
        return SetInspectNoticeResponse.newBuilder()
                .setEnabled(facades().inspectNotice().enabled(characterId))
                .build();
    }

    /** ADR-330 — soi thành công người khác ⇒ người đó (đang online, bật báo) nhận một dòng. Trả nguyên kết quả soi. */
    // PMD AvoidCatchingGenericException: việc phụ — lỗi không được làm hỏng kết quả soi.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    InspectCharacterResponse notifyInspected(long viewerId, InspectCharacterResponse inspected) {
        if (inspected.getOutcome() != InspectCharacterOutcome.INSPECT_CHARACTER_OUTCOME_OK) {
            return inspected;
        }
        try {
            gateway.characterFacade()
                    .findByName(inspected.getName())
                    .filter(target -> target.id() != viewerId && registry.isOnline(target.id()))
                    .filter(target -> facades().inspectNotice().enabled(target.id()))
                    .ifPresent(target -> gateway.characterFacade()
                            .findById(viewerId)
                            .map(Character::name)
                            .ifPresent(viewer -> notice(target.id(), inspectedNotice(viewer))));
        } catch (RuntimeException e) {
            LOG.warn("Không báo được người bị soi: viewerId={}", viewerId, e);
        }
        return inspected;
    }

    /** Dòng báo bị soi. Tĩnh để test. */
    static byte[] inspectedNotice(String viewer) {
        return line(viewer + " vừa xem trang bị của bạn. / " + viewer + " just inspected your equipment.");
    }

    private void notice(long characterId, byte[] payload) {
        registry.sendToCharacterIfOnline(characterId, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, payload);
    }

    private static byte[] line(String body) {
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody(body)
                .build()
                .toByteArray();
    }

    private static LocalDate today() {
        return LocalDate.ofInstant(Instant.now(), DailyReset.ZONE);
    }

    private dev.kage.account.api.LoginHistoryFacade loginHistory() {
        return gateway.progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .lot38()
                .loginHistory();
    }

    private Lot50Facades facades() {
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
                .lot50();
    }
}
