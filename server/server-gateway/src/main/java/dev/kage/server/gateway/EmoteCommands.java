package dev.kage.server.gateway;

import java.util.Optional;
import java.util.function.LongSupplier;

import dev.kage.protocol.Emote;
import dev.kage.protocol.EmoteEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendEmoteOutcome;
import dev.kage.protocol.SendEmoteResponse;
import dev.kage.server.world.WorldFacade;

/**
 * Biểu cảm (ADR-092) — không DB, chạy thẳng trên EventLoop: kiểm mã, kiểm nhịp, rồi nhờ World gửi {@code EmoteEvent}
 * cho mọi người đang nhìn thấy (kèm chính mình) ở tick kế. Một bản mỗi kết nối — nhịp tính theo kết nối, mà mỗi nhân
 * vật chỉ có một kết nối (quyết định một-phiên 2026-09-25).
 */
final class EmoteCommands {

    /** Một biểu cảm mỗi ngần này mili-giây — đủ để chào nhau, không đủ để xả bong bóng che màn hình người khác. */
    static final long MIN_INTERVAL_MILLIS = 2_000L;

    private final ConnectedCharacterRegistry registry;
    private final LongSupplier clockMillis;
    private long lastEmoteAtMillis = Long.MIN_VALUE;

    EmoteCommands(ConnectedCharacterRegistry registry, LongSupplier clockMillis) {
        this.registry = registry;
        this.clockMillis = clockMillis;
    }

    SendEmoteResponse send(long characterId, Emote emote, Optional<WorldFacade> world) {
        if (emote == Emote.EMOTE_UNSPECIFIED || emote == Emote.UNRECOGNIZED) {
            return response(SendEmoteOutcome.SEND_EMOTE_OUTCOME_INVALID_EMOTE);
        }
        if (world.isEmpty()) {
            return response(SendEmoteOutcome.SEND_EMOTE_OUTCOME_NOT_IN_WORLD);
        }
        long now = clockMillis.getAsLong();
        if (lastEmoteAtMillis != Long.MIN_VALUE && now - lastEmoteAtMillis < MIN_INTERVAL_MILLIS) {
            return response(SendEmoteOutcome.SEND_EMOTE_OUTCOME_RATE_LIMITED);
        }
        lastEmoteAtMillis = now;
        byte[] event = EmoteEvent.newBuilder()
                .setCharacterId(characterId)
                .setEmote(emote)
                .build()
                .toByteArray();
        world.get()
                .enqueueForObservers(
                        characterId,
                        observerId -> registry.sendToCharacterIfOnline(
                                observerId, Opcode.OPCODE_EMOTE_EVENT_VALUE, 0, event));
        return response(SendEmoteOutcome.SEND_EMOTE_OUTCOME_OK);
    }

    private static SendEmoteResponse response(SendEmoteOutcome outcome) {
        return SendEmoteResponse.newBuilder().setOutcome(outcome).build();
    }
}
