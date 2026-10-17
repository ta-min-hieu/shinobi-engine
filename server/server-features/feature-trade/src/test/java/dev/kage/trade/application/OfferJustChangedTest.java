package dev.kage.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.trade.domain.TradeSession;
import dev.kage.trade.domain.TransactionRunner;

/**
 * README dòng 25(i)(b): sửa lời chào chặn ĐỐI PHƯƠNG xác nhận trong {@link TradeOfferChanges#BLOCK_WINDOW}
 * — đường "tráo lời chào phút chót" (B bấm xác nhận trên màn hình cũ, lệnh tới ngay sau lần A sửa).
 */
class OfferJustChangedTest {

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);
    private static final long A = 1;
    private static final long B = 5;

    private final StepClock clock = new StepClock();
    private final TradeOfferChanges offerChanges = new TradeOfferChanges(clock);
    private final FakeTradeSessionPort sessionPort = new FakeTradeSessionPort();
    private final FakeIdempotencyPort idempotencyPort = new FakeIdempotencyPort();
    private final SetTradeOfferUseCase setOffer = new SetTradeOfferUseCase(sessionPort, offerChanges);
    private final ConfirmTradeUseCase confirm = new ConfirmTradeUseCase(
            new TradePorts(
                    sessionPort,
                    new FakeTradeInventoryPort(),
                    new FakeTradeGoldPort(),
                    new FakeTradeAuditPort(),
                    idempotencyPort),
            DIRECT_TX,
            offerChanges);

    private long openSession() {
        TradeSession session = sessionPort.createRequest(A, B);
        sessionPort.acceptRequest(session.id(), B);
        return session.id();
    }

    @Test
    void shouldRejectCounterpartConfirm_rightAfterOfferChanged() {
        long sessionId = openSession();
        setOffer.execute(sessionId, A, 0, List.of(new OfferedItemInput(42, 1)));

        clock.advance(Duration.ofSeconds(2));

        assertThat(confirm.execute(sessionId, B, "key-b")).isEqualTo(ConfirmTradeUseCase.Result.OFFER_JUST_CHANGED);
        assertThat(sessionPort.findSnapshot(sessionId).orElseThrow().targetConfirmed())
                .isFalse();
    }

    @Test
    void shouldAcceptCounterpartConfirm_onceTheWindowHasPassed_withTheSameIdempotencyKey() {
        long sessionId = openSession();
        setOffer.execute(sessionId, A, 0, List.of());
        confirm.execute(sessionId, B, "key-b");

        clock.advance(TradeOfferChanges.BLOCK_WINDOW);

        // Lần bị chặn không ghi khoá idempotency — bấm lại với cùng khoá vẫn được.
        assertThat(confirm.execute(sessionId, B, "key-b")).isEqualTo(ConfirmTradeUseCase.Result.OK);
    }

    @Test
    void shouldLetTheChangerConfirmTheirOwnOffer_immediately() {
        long sessionId = openSession();
        setOffer.execute(sessionId, A, 0, List.of());

        assertThat(confirm.execute(sessionId, A, "key-a")).isEqualTo(ConfirmTradeUseCase.Result.OK);
    }

    @Test
    void shouldNotBlockOtherSessions() {
        long sessionId = openSession();
        setOffer.execute(sessionId, A, 0, List.of());
        TradeSession other = sessionPort.createRequest(7, 8);
        sessionPort.acceptRequest(other.id(), 8);

        assertThat(confirm.execute(other.id(), 8, "key-8")).isEqualTo(ConfirmTradeUseCase.Result.OK);
    }

    @Test
    void shouldNotRecordAChange_whenTheOfferWasRejected() {
        TradeSession pending = sessionPort.createRequest(A, B); // chưa nhận lời ⇒ replaceOffer trả false
        setOffer.execute(pending.id(), A, 0, List.of());
        sessionPort.acceptRequest(pending.id(), B);

        assertThat(confirm.execute(pending.id(), B, "key-b")).isEqualTo(ConfirmTradeUseCase.Result.OK);
    }

    /** Đồng hồ chỉ chạy khi test bảo. */
    private static final class StepClock extends Clock {
        private Instant now = Instant.parse("2026-10-17T00:00:00Z");

        void advance(Duration step) {
            now = now.plus(step);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
