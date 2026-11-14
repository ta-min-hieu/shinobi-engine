package dev.kage.server.gateway;

import java.time.Instant;
import java.util.Optional;

import dev.kage.character.api.ProfileLikeFacade;
import dev.kage.character.domain.Character;
import dev.kage.mail.api.ScheduledMailFacade;
import dev.kage.mail.domain.ScheduledMailPort;
import dev.kage.monster.domain.BossKillLogPort;
import dev.kage.protocol.BossKillEntry;
import dev.kage.protocol.GetBossKillsResponse;
import dev.kage.protocol.LikeProfileCommand;
import dev.kage.protocol.LikeProfileOutcome;
import dev.kage.protocol.LikeProfileResponse;
import dev.kage.protocol.ScheduleMailCommand;
import dev.kage.protocol.ScheduleMailOutcome;
import dev.kage.protocol.ScheduleMailResponse;

/** Lớp lệnh lô 46 (ADR-295…) — MỘT thành phần của {@link Lot45Commands}. Gọi trên Virtual Thread. */
record Lot46Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** Lớp lệnh lô 47 (ADR-303…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot47Commands lot47() {
        return new Lot47Commands(gateway, registry);
    }

    /** Khớp {@code VARCHAR(64)} / {@code VARCHAR(1024)} của bảng thư. */
    static final int MAX_SUBJECT = 64;

    static final int MAX_BODY = 1024;

    /** ADR-296 — 20 lần hạ boss gần nhất. */
    GetBossKillsResponse bossKills(long characterId) {
        GetBossKillsResponse.Builder response = GetBossKillsResponse.newBuilder();
        for (BossKillLogPort.Entry entry : registry.bossKills.recent(characterId)) {
            response.addEntries(BossKillEntry.newBuilder()
                    .setBossCode(entry.bossCode())
                    .setWorldBoss(entry.kind() == BossKillLogPort.Kind.WORLD)
                    .setRank(entry.rank())
                    .setDamage(entry.damage())
                    .setAtEpochSeconds(entry.at().getEpochSecond()));
        }
        return response.build();
    }

    /** ADR-297 — hẹn một thư chữ; tới giờ {@link ScheduledMailJob} gửi qua đường thư thường. */
    ScheduleMailResponse scheduleMail(long characterId, ScheduleMailCommand command) {
        ScheduledMailFacade scheduled = facades().scheduledMail();
        ScheduleMailResponse.Builder response = ScheduleMailResponse.newBuilder();
        Optional<Character> recipient =
                gateway.characterFacade().findByName(command.getRecipientName().strip());
        if (recipient.isEmpty()) {
            return response.setOutcome(ScheduleMailOutcome.SCHEDULE_MAIL_OUTCOME_RECIPIENT_NOT_FOUND)
                    .setPending(scheduled.pendingCount(characterId))
                    .build();
        }
        String subject = command.getSubject().strip();
        if (!validText(subject, command.getBody())) {
            return response.setOutcome(ScheduleMailOutcome.SCHEDULE_MAIL_OUTCOME_INVALID_TEXT)
                    .setPending(scheduled.pendingCount(characterId))
                    .build();
        }
        ScheduledMailFacade.Outcome outcome = scheduled.schedule(
                new ScheduledMailPort.Pending(
                        0L, characterId, recipient.get().id(), subject, command.getBody(), Instant.EPOCH),
                command.getDelayMinutes(),
                Instant.now());
        return response.setOutcome(ScheduleMailOutcome.valueOf("SCHEDULE_MAIL_OUTCOME_" + outcome.name()))
                .setPending(scheduled.pendingCount(characterId))
                .build();
    }

    /** Tiêu đề 1–64 ký tự, nội dung ≤ 1024, không ký tự NUL. Tĩnh để test. */
    static boolean validText(String subject, String body) {
        return !subject.isEmpty()
                && subject.length() <= MAX_SUBJECT
                && body.length() <= MAX_BODY
                && subject.indexOf('\u0000') < 0
                && body.indexOf('\u0000') < 0;
    }

    /** ADR-298 — thích hồ sơ một người (mỗi ngày một lần). */
    LikeProfileResponse likeProfile(long characterId, LikeProfileCommand command) {
        Optional<Character> target =
                gateway.characterFacade().findByName(command.getTargetName().strip());
        if (target.isEmpty()) {
            return LikeProfileResponse.newBuilder()
                    .setOutcome(LikeProfileOutcome.LIKE_PROFILE_OUTCOME_NOT_FOUND)
                    .setTargetName(command.getTargetName())
                    .build();
        }
        ProfileLikeFacade likes = facades().profileLikes();
        ProfileLikeFacade.Outcome outcome = likes.like(characterId, target.get().id());
        return LikeProfileResponse.newBuilder()
                .setOutcome(LikeProfileOutcome.valueOf("LIKE_PROFILE_OUTCOME_" + outcome.name()))
                .setTargetName(target.get().name())
                .setLikes(likes.likesOf(target.get().id()))
                .build();
    }

    /** ADR-298 — gắn tổng lượt thích vào kết quả soi trang bị (chỉ khi soi được). */
    dev.kage.protocol.InspectCharacterResponse withLikes(dev.kage.protocol.InspectCharacterResponse inspected) {
        if (inspected.getOutcome() != dev.kage.protocol.InspectCharacterOutcome.INSPECT_CHARACTER_OUTCOME_OK) {
            return inspected;
        }
        return gateway.characterFacade()
                .findByName(inspected.getName())
                .map(found -> inspected.toBuilder()
                        .setLikes(facades().profileLikes().likesOf(found.id()))
                        .build())
                .orElse(inspected);
    }

    /** ADR-362 lọc chỉ-bạn-bè + ADR-361 ghi lịch sử; ADR-330 / ADR-344 lượt thích, ký hiệu bang, báo người bị soi. */
    dev.kage.protocol.InspectCharacterResponse withLikesAndNotice(
            long viewerId, dev.kage.protocol.InspectCharacterResponse inspected) {
        Lot50Commands lot50 = lot47().lot48().lot49().lot50();
        dev.kage.protocol.InspectCharacterResponse visible =
                lot50.lot51().lot52().lot53().lot54().guardInspect(viewerId, inspected);
        return lot50.notifyInspected(viewerId, lot50.lot51().lot52().withGuildTag(withLikes(visible))); // ADR-344
    }

    private Lot46Facades facades() {
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
