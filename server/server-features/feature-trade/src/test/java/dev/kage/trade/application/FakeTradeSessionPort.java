package dev.kage.trade.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.trade.domain.TradeOfferItem;
import dev.kage.trade.domain.TradeSession;
import dev.kage.trade.domain.TradeSessionPort;
import dev.kage.trade.domain.TradeSessionSnapshot;
import dev.kage.trade.domain.TradeStatus;
import dev.kage.trade.domain.Transaction;

/**
 * Fake in-memory THẬT (TESTING.md §4), không Mockito — DÙNG CHUNG cho toàn bộ test
 * {@code application/} (cùng quyết định feature-guild/feature-party — nhiều method, tránh lặp
 * nested Fake mỗi file test).
 */
final class FakeTradeSessionPort implements TradeSessionPort {

    private final Map<Long, MutableState> sessions = new LinkedHashMap<>();
    private final AtomicLong nextSessionId = new AtomicLong(1);
    private final AtomicLong nextOfferItemId = new AtomicLong(1);

    private static final class MutableState {
        long id;
        long requesterCharacterId;
        long targetCharacterId;
        TradeStatus status;
        long requesterGold;
        long targetGold;
        boolean requesterConfirmed;
        boolean targetConfirmed;
        Instant createdAt;
        final List<TradeOfferItem> items = new ArrayList<>();
    }

    @Override
    public Optional<TradeSession> findActiveSessionOfCharacter(long characterId) {
        return sessions.values().stream()
                .filter(s -> s.requesterCharacterId == characterId || s.targetCharacterId == characterId)
                .findFirst()
                .map(FakeTradeSessionPort::toSession);
    }

    @Override
    public TradeSession createRequest(long requesterCharacterId, long targetCharacterId) {
        MutableState state = new MutableState();
        state.id = nextSessionId.getAndIncrement();
        state.requesterCharacterId = requesterCharacterId;
        state.targetCharacterId = targetCharacterId;
        state.status = TradeStatus.PENDING_ACCEPT;
        state.createdAt = Instant.now();
        sessions.put(state.id, state);
        return toSession(state);
    }

    @Override
    public boolean acceptRequest(long sessionId, long characterId) {
        MutableState state = sessions.get(sessionId);
        if (state == null || state.targetCharacterId != characterId || state.status != TradeStatus.PENDING_ACCEPT) {
            return false;
        }
        state.status = TradeStatus.OPEN;
        return true;
    }

    @Override
    public boolean deleteSessionIfParticipant(long sessionId, long characterId) {
        MutableState state = sessions.get(sessionId);
        if (state == null || (state.requesterCharacterId != characterId && state.targetCharacterId != characterId)) {
            return false;
        }
        sessions.remove(sessionId);
        return true;
    }

    @Override
    public boolean replaceOffer(long sessionId, long characterId, long goldAmount, List<OfferedItemInput> items) {
        MutableState state = sessions.get(sessionId);
        if (state == null
                || state.status != TradeStatus.OPEN
                || (state.requesterCharacterId != characterId && state.targetCharacterId != characterId)) {
            return false;
        }
        state.items.removeIf(i -> i.offeringCharacterId() == characterId);
        for (OfferedItemInput item : items) {
            state.items.add(new TradeOfferItem(
                    nextOfferItemId.getAndIncrement(), sessionId, characterId, item.itemId(), item.quantity()));
        }
        if (characterId == state.requesterCharacterId) {
            state.requesterGold = goldAmount;
        } else {
            state.targetGold = goldAmount;
        }
        state.requesterConfirmed = false;
        state.targetConfirmed = false;
        return true;
    }

    @Override
    public Optional<TradeSessionSnapshot> findSnapshot(long sessionId) {
        MutableState state = sessions.get(sessionId);
        return state == null ? Optional.empty() : Optional.of(toSnapshot(state));
    }

    @Override
    public Optional<TradeSessionSnapshot> lockForExecution(Transaction tx, long sessionId) {
        return findSnapshot(sessionId);
    }

    @Override
    public void setConfirmed(Transaction tx, long sessionId, long characterId) {
        MutableState state = sessions.get(sessionId);
        if (state == null) {
            return;
        }
        if (characterId == state.requesterCharacterId) {
            state.requesterConfirmed = true;
        } else if (characterId == state.targetCharacterId) {
            state.targetConfirmed = true;
        }
    }

    @Override
    public void deleteSession(Transaction tx, long sessionId) {
        sessions.remove(sessionId);
    }

    private static TradeSession toSession(MutableState s) {
        return new TradeSession(s.id, s.requesterCharacterId, s.targetCharacterId, s.status, s.createdAt);
    }

    private static TradeSessionSnapshot toSnapshot(MutableState s) {
        return new TradeSessionSnapshot(
                s.id,
                s.requesterCharacterId,
                s.targetCharacterId,
                s.status,
                s.requesterGold,
                s.targetGold,
                s.requesterConfirmed,
                s.targetConfirmed,
                List.copyOf(s.items));
    }
}
