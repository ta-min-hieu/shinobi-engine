package dev.kage.server.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.checkin.api.ActivityFacade;
import dev.kage.checkin.domain.ActivityPort;
import dev.kage.gm.domain.GiftCode;
import dev.kage.gm.domain.GiftCodeOutcome;
import dev.kage.gm.domain.GiftCodePort;
import dev.kage.gm.domain.PlayerReport;
import dev.kage.gm.domain.ReportOutcome;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.ActivityChestView;
import dev.kage.protocol.ActivityView;
import dev.kage.protocol.ClaimActivityChestOutcome;
import dev.kage.protocol.ClaimActivityChestResponse;
import dev.kage.protocol.GetActivityResponse;
import dev.kage.protocol.NewMailEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RedeemGiftCodeOutcome;
import dev.kage.protocol.RedeemGiftCodeResponse;
import dev.kage.protocol.ReportPlayerCommand;
import dev.kage.protocol.ReportPlayerOutcome;
import dev.kage.protocol.ReportPlayerResponse;
import dev.kage.protocol.ServerNoticeEvent;

/**
 * Phần người chơi của lô 22 phần 1 — thông báo lúc vào world (ADR-103), đổi mã quà (ADR-104), báo cáo người chơi
 * (ADR-105), lịch hoạt động (ADR-106). Chạy trên Virtual Thread (chạm DB). Phần GM nằm ở {@link GmDeskCommands}.
 */
final class ServiceDeskCommands {

    /** Trần đổi mã mỗi phút mỗi nhân vật (ADR-104 D5) — chống dò mã. */
    static final int MAX_REDEEMS_PER_MINUTE = 10;

    private static final long MINUTE_MILLIS = 60_000L;
    private static final String GIFT_SUBJECT = "Mã quà / Gift code";
    private static final String CHEST_SUBJECT = "Rương năng động / Activity chest";

    /** Các nguồn dữ liệu — gom để constructor không vượt 4 tham số. */
    record Deps(
            ServiceDeskFacades desk,
            CharacterFacade characters,
            MailFacade mail,
            ConnectedCharacterRegistry registry) {}

    /** Cửa sổ đếm đổi mã của một nhân vật. */
    private record RedeemWindow(long startedMillis, int count) {}

    private final Deps deps;
    private final Clock clock;
    private final ConcurrentHashMap<Long, RedeemWindow> redeemWindows = new ConcurrentHashMap<>();

    ServiceDeskCommands(Deps deps, Clock clock) {
        this.deps = deps;
        this.clock = clock;
    }

    static ServiceDeskCommands of(GatewayFacades facades, ConnectedCharacterRegistry registry) {
        return new ServiceDeskCommands(
                new Deps(
                        facades.progression().serviceDesk(),
                        facades.characterFacade(),
                        facades.contentFacades().socialFacades().mailFacade(),
                        registry),
                Clock.systemUTC());
    }

    /** Phần GM cùng nguồn dữ liệu ({@code /gm motd|code|reports|report|resolve}). */
    GmDeskCommands gmDesk(InventoryFacade inventory) {
        return new GmDeskCommands(new GmDeskCommands.Deps(deps.desk(), inventory, deps.registry()), clock);
    }

    /** ADR-103 D3 — gửi thông báo (nếu có) cho người vừa vào world. Không chạm DB. */
    void sendNoticeOnEnter(long characterId) {
        deps.desk().notice().current().ifPresent(body -> deps.registry()
                .sendToCharacterIfOnline(
                        characterId,
                        Opcode.OPCODE_SERVER_NOTICE_EVENT_VALUE,
                        0,
                        ServerNoticeEvent.newBuilder().setBody(body).build().toByteArray()));
    }

    /** ADR-104 D3 — đổi mã; quà đi bằng thư chống trùng suy từ (mã, nhân vật). */
    RedeemGiftCodeResponse redeem(long characterId, String rawCode) {
        if (!allowRedeem(characterId)) {
            throw new CommandRefusedException(429, "error.rate_limited");
        }
        GiftCodePort.Redemption redemption = deps.desk().giftCodes().redeem(rawCode, characterId, clock.instant());
        RedeemGiftCodeOutcome outcome = toProto(redemption.outcome());
        if (redemption.outcome() == GiftCodeOutcome.OK && redemption.needsMail()) {
            GiftCode code = redemption.code().orElseThrow();
            if (mailGift(characterId, code)) {
                deps.desk().giftCodes().markMailed(code.code(), characterId);
            } else {
                outcome = RedeemGiftCodeOutcome.REDEEM_GIFT_CODE_OUTCOME_MAIL_FAILED;
            }
        }
        return RedeemGiftCodeResponse.newBuilder().setOutcome(outcome).build();
    }

    private boolean allowRedeem(long characterId) {
        long now = clock.millis();
        RedeemWindow window = redeemWindows.compute(
                characterId,
                (id, current) -> current == null || now - current.startedMillis() >= MINUTE_MILLIS
                        ? new RedeemWindow(now, 1)
                        : new RedeemWindow(current.startedMillis(), current.count() + 1));
        return window.count() <= MAX_REDEEMS_PER_MINUTE;
    }

    private boolean mailGift(long characterId, GiftCode code) {
        List<MailFacade.AttachmentRequest> items = code.hasItem()
                ? List.of(new MailFacade.AttachmentRequest(code.itemTemplateId(), code.quantity()))
                : List.of();
        return sendSystemMail(
                characterId,
                new MailFacade.SendMailRequest(
                        null,
                        characterId,
                        GIFT_SUBJECT,
                        "Quà của mã %s. / Your gift for code %s.".formatted(code.code(), code.code()),
                        code.gold(),
                        items),
                "gift-code-" + code.code() + "-" + characterId);
    }

    private static RedeemGiftCodeOutcome toProto(GiftCodeOutcome outcome) {
        return RedeemGiftCodeOutcome.valueOf("REDEEM_GIFT_CODE_OUTCOME_" + outcome.name());
    }

    /** ADR-105 D2 — báo cáo một người chơi theo tên. */
    ReportPlayerResponse report(long characterId, ReportPlayerCommand command) {
        Optional<PlayerReport.Reason> reason =
                switch (command.getReason()) {
                    case REPORT_REASON_CHAT -> Optional.of(PlayerReport.Reason.CHAT);
                    case REPORT_REASON_CHEAT -> Optional.of(PlayerReport.Reason.CHEAT);
                    case REPORT_REASON_SCAM -> Optional.of(PlayerReport.Reason.SCAM);
                    case REPORT_REASON_OTHER -> Optional.of(PlayerReport.Reason.OTHER);
                    case REPORT_REASON_UNSPECIFIED, UNRECOGNIZED -> Optional.empty();
                };
        if (reason.isEmpty()) {
            return reportOutcome(ReportPlayerOutcome.REPORT_PLAYER_OUTCOME_INVALID_REASON);
        }
        Optional<Character> target =
                deps.characters().findByName(command.getTargetName().strip());
        if (target.isEmpty()) {
            return reportOutcome(ReportPlayerOutcome.REPORT_PLAYER_OUTCOME_UNKNOWN_TARGET);
        }
        ReportOutcome outcome =
                deps.desk().reports().file(characterId, target.get().id(), reason.get(), command.getNote());
        return reportOutcome(ReportPlayerOutcome.valueOf("REPORT_PLAYER_OUTCOME_" + outcome.name()));
    }

    private static ReportPlayerResponse reportOutcome(ReportPlayerOutcome outcome) {
        return ReportPlayerResponse.newBuilder().setOutcome(outcome).build();
    }

    /** ADR-106 — trạng thái điểm năng động hôm nay. */
    GetActivityResponse activity(long characterId) {
        ActivityFacade.Status status = deps.desk().activity().status(characterId);
        GetActivityResponse.Builder response = GetActivityResponse.newBuilder().setPoints(status.points());
        for (ActivityFacade.ActivityView view : status.activities()) {
            response.addActivities(ActivityView.newBuilder()
                    .setCode(view.definition().code())
                    .setNameKey(view.definition().nameKey())
                    .setCount(view.count())
                    .setMaxCount(view.definition().maxCount())
                    .setPointsEach(view.definition().pointsEach())
                    .setPoints(view.points()));
        }
        for (ActivityFacade.ChestView view : status.chests()) {
            response.addChests(ActivityChestView.newBuilder()
                    .setMilestone(view.chest().milestone())
                    .setGold(view.chest().gold())
                    .setItemTemplateId(Math.max(0L, view.chest().itemTemplateId()))
                    .setQuantity(view.chest().quantity())
                    .setReached(view.reached())
                    .setClaimed(view.claimed()));
        }
        return response.build();
    }

    /** ADR-106 D3 — nhận rương mốc; quà qua thư chống trùng suy từ (nhân vật, ngày, mốc). */
    ClaimActivityChestResponse claimChest(long characterId, int milestone) {
        Optional<ActivityPort.Chest> chest = deps.desk().activity().claim(characterId, milestone);
        chest.ifPresent(c -> mailChest(characterId, c));
        return ClaimActivityChestResponse.newBuilder()
                .setOutcome(
                        chest.isPresent()
                                ? ClaimActivityChestOutcome.CLAIM_ACTIVITY_CHEST_OUTCOME_OK
                                : ClaimActivityChestOutcome.CLAIM_ACTIVITY_CHEST_OUTCOME_NOT_AVAILABLE)
                .setStatus(activity(characterId))
                .build();
    }

    private void mailChest(long characterId, ActivityPort.Chest chest) {
        List<MailFacade.AttachmentRequest> items = chest.itemTemplateId() > 0 && chest.quantity() > 0
                ? List.of(new MailFacade.AttachmentRequest(chest.itemTemplateId(), chest.quantity()))
                : List.of();
        sendSystemMail(
                characterId,
                new MailFacade.SendMailRequest(
                        null,
                        characterId,
                        CHEST_SUBJECT,
                        "Đạt %d điểm năng động hôm nay. / %d activity points today."
                                .formatted(chest.milestone(), chest.milestone()),
                        chest.gold(),
                        items),
                "activity-chest-" + characterId + "-" + deps.desk().activity().today() + "-" + chest.milestone());
    }

    /** {@code true} nếu thư đã nằm trong hộp (gửi lần này hoặc trước đó); báo thư mới nếu vừa gửi. */
    private boolean sendSystemMail(long characterId, MailFacade.SendMailRequest request, String keySeed) {
        String key =
                UUID.nameUUIDFromBytes(keySeed.getBytes(StandardCharsets.UTF_8)).toString();
        MailFacade.SendOutcome outcome = deps.mail().sendMail(request, key);
        if (outcome.result() == MailFacade.SendResult.OK) {
            deps.registry()
                    .sendToCharacterIfOnline(
                            characterId,
                            Opcode.OPCODE_NEW_MAIL_EVENT_VALUE,
                            0,
                            NewMailEvent.newBuilder()
                                    .setMailId(outcome.mailId())
                                    .build()
                                    .toByteArray());
            return true;
        }
        return outcome.result() == MailFacade.SendResult.DUPLICATE_REQUEST;
    }
}
