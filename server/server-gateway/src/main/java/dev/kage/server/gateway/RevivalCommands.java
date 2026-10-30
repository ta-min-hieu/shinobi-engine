package dev.kage.server.gateway;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import dev.kage.protocol.AwaitingRevivalEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.ReviveResponse;
import dev.kage.server.world.RevivalListener;
import dev.kage.server.world.ReviveOutcome;
import dev.kage.server.world.WorldFacade;
import dev.kage.world.api.ConsumableFacade;

/**
 * Hồi sinh tại chỗ (ADR-117) — chạy trên Virtual Thread. Đứng dậy tại chỗ: tiêu Hồi Hồn Đan TRƯỚC (DB), rồi hỏi World;
 * World từ chối (đã tự về điểm hồi sinh, bị người chơi hạ...) thì hoàn đan. Chờ câu trả lời của tick thread có trần —
 * quá trần coi như World không nhận, hoàn đan.
 */
final class RevivalCommands {

    private static final long WORLD_REPLY_TIMEOUT_MILLIS = 2_000L;

    private final ConsumableFacade consumables;

    RevivalCommands(ConsumableFacade consumables) {
        this.consumables = consumables;
    }

    /** Người nghe của world chính: báo CHÍNH người gục rằng họ đang nằm chờ chọn. */
    static RevivalListener listener(ConnectedCharacterRegistry registry) {
        return (characterId, seconds, canReviveInPlace) -> registry.sendToCharacterIfOnline(
                characterId,
                Opcode.OPCODE_AWAITING_REVIVAL_EVENT_VALUE,
                0,
                AwaitingRevivalEvent.newBuilder()
                        .setSeconds(seconds)
                        .setCanReviveInPlace(canReviveInPlace)
                        .build()
                        .toByteArray());
    }

    ReviveResponse revive(long characterId, boolean inPlace, Optional<WorldFacade> world) {
        if (world.isEmpty()) {
            return response(dev.kage.protocol.ReviveOutcome.REVIVE_OUTCOME_NOT_DEAD);
        }
        if (inPlace && !consumables.consume(characterId, ConsumableFacade.REVIVE_PILL)) {
            return response(dev.kage.protocol.ReviveOutcome.REVIVE_OUTCOME_NO_PILL);
        }
        ReviveOutcome outcome = ask(world.get(), characterId, inPlace);
        if (inPlace && outcome != ReviveOutcome.OK) {
            consumables.refund(characterId, ConsumableFacade.REVIVE_PILL);
        }
        return response(
                switch (outcome) {
                    case OK -> dev.kage.protocol.ReviveOutcome.REVIVE_OUTCOME_OK;
                    case FORBIDDEN -> dev.kage.protocol.ReviveOutcome.REVIVE_OUTCOME_FORBIDDEN;
                    case NOT_DEAD -> dev.kage.protocol.ReviveOutcome.REVIVE_OUTCOME_NOT_DEAD;
                });
    }

    private static ReviveOutcome ask(WorldFacade world, long characterId, boolean inPlace) {
        CompletableFuture<ReviveOutcome> reply = new CompletableFuture<>();
        world.revive(characterId, inPlace, reply::complete);
        try {
            return reply.get(WORLD_REPLY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ReviveOutcome.NOT_DEAD;
        } catch (ExecutionException | TimeoutException e) {
            return ReviveOutcome.NOT_DEAD;
        }
    }

    private static ReviveResponse response(dev.kage.protocol.ReviveOutcome outcome) {
        return ReviveResponse.newBuilder().setOutcome(outcome).build();
    }
}
