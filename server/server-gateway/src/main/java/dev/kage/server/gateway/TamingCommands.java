package dev.kage.server.gateway;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.TameMonsterCommand;
import dev.kage.protocol.TameMonsterResponse;
import dev.kage.protocol.TameOutcome;
import dev.kage.server.world.CaptureResult;
import dev.kage.server.world.CaptureRule;
import dev.kage.server.world.WorldFacade;
import dev.kage.world.api.ConsumableFacade;
import dev.kage.world.api.TamingFacade;
import dev.kage.world.domain.TamingPort;
import dev.kage.world.domain.TamingRules;

/**
 * Thu phục yêu thú (ADR-124) — chạy trên Virtual Thread. Tiêu Phong Yêu Phù TRƯỚC (DB), rồi hỏi World (kiểm tầm, máu,
 * loài, tung tỉ lệ và gỡ quái trong một lượt tick); World từ chối vì điều kiện (xa, còn nhiều máu, loài không thu
 * phục được, không thấy quái, quá trần chờ) thì hoàn phù — chỉ lần tung trượt mới mất phù. Bắt được mà túi đầy thì linh
 * thú đi qua thư, không mất.
 */
final class TamingCommands {

    private static final long WORLD_REPLY_TIMEOUT_MILLIS = 2_000L;
    private static final String MAIL_SUBJECT = "Thu phục yêu thú / Monster taming";
    private static final String MAIL_BODY =
            "Túi đầy — linh thú gửi qua thư. / Bag full — your new pet arrived by mail.";

    private final TamingFacade taming;
    private final ConsumableFacade consumables;
    private final CharacterFacade characters;
    private final PlayerNotices notices;

    TamingCommands(
            TamingFacade taming, ConsumableFacade consumables, CharacterFacade characters, PlayerNotices notices) {
        this.taming = taming;
        this.consumables = consumables;
        this.characters = characters;
        this.notices = notices;
    }

    TameMonsterResponse tame(long characterId, TameMonsterCommand command, Optional<WorldFacade> world) {
        if (world.isEmpty()) {
            return response(TameOutcome.TAME_OUTCOME_NOT_FOUND, 0L, false);
        }
        if (!consumables.consume(characterId, TamingRules.TALISMAN_CODE)) {
            return response(TameOutcome.TAME_OUTCOME_NO_TALISMAN, 0L, false);
        }
        int level = characters.findById(characterId).map(Character::level).orElse(1);
        Map<String, Integer> chances = taming.chancesFor(level); // tính ở đây — tick thread chỉ tra bảng
        CaptureRule rule = new CaptureRule(
                TamingRules.RANGE_METERS, TamingRules.MAX_HP_PERCENT, code -> TamingFacade.lookup(chances, code));
        CaptureResult result = ask(world.get(), characterId, command.getMonsterId(), rule);
        return switch (result.outcome()) {
            case CAPTURED -> captured(characterId, result.monsterCode());
            case ESCAPED -> response(TameOutcome.TAME_OUTCOME_ESCAPED, 0L, false);
            case NOT_FOUND -> refunded(characterId, TameOutcome.TAME_OUTCOME_NOT_FOUND);
            case TOO_FAR -> refunded(characterId, TameOutcome.TAME_OUTCOME_TOO_FAR);
            case HP_TOO_HIGH -> refunded(characterId, TameOutcome.TAME_OUTCOME_HP_TOO_HIGH);
            case NOT_TAMEABLE -> refunded(characterId, TameOutcome.TAME_OUTCOME_NOT_TAMEABLE);
        };
    }

    private TameMonsterResponse captured(long characterId, String monsterCode) {
        Optional<TamingPort.Tameable> tameable = taming.tameable(monsterCode);
        if (tameable.isEmpty()) {
            return response(TameOutcome.TAME_OUTCOME_NOT_TAMEABLE, 0L, false); // world đã hỏi chính facade này
        }
        long pet = tameable.get().petItemTemplateId();
        if (taming.grantPet(characterId, pet, monsterCode)) {
            return response(TameOutcome.TAME_OUTCOME_CAPTURED, pet, false);
        }
        notices.mail(
                characterId,
                new PlayerNotices.SystemMail(
                        MAIL_SUBJECT, MAIL_BODY, 0L, List.of(new MailFacade.AttachmentRequest(pet, 1, 0))));
        return response(TameOutcome.TAME_OUTCOME_CAPTURED, pet, true);
    }

    private TameMonsterResponse refunded(long characterId, TameOutcome outcome) {
        consumables.refund(characterId, TamingRules.TALISMAN_CODE);
        return response(outcome, 0L, false);
    }

    private static CaptureResult ask(WorldFacade world, long characterId, long monsterId, CaptureRule rule) {
        CompletableFuture<CaptureResult> reply = new CompletableFuture<>();
        world.captureMonster(characterId, monsterId, rule, reply::complete);
        try {
            return reply.get(WORLD_REPLY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CaptureResult(CaptureResult.Outcome.NOT_FOUND, "");
        } catch (ExecutionException | TimeoutException e) {
            return new CaptureResult(CaptureResult.Outcome.NOT_FOUND, "");
        }
    }

    private static TameMonsterResponse response(TameOutcome outcome, long petItemTemplateId, boolean mailed) {
        return TameMonsterResponse.newBuilder()
                .setOutcome(outcome)
                .setPetItemTemplateId(petItemTemplateId)
                .setMailed(mailed)
                .build();
    }
}
