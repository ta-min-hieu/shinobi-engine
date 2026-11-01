package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.BossTelegraphEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/** ADR-145 — vòng báo trước sống đúng số giây server nói rồi tắt; gói hỏng không treo vòng. */
class BossTelegraphPresenterTest {

    private final BossTelegraphPresenter telegraphs = new BossTelegraphPresenter();

    @Test
    void shouldShowTheRingForTheAnnouncedSecondsOnly() throws InvalidProtocolBufferException {
        BossTelegraphEvent event = BossTelegraphEvent.newBuilder()
                .setMonsterId(-25L)
                .setX(408f)
                .setRadius(4f)
                .setSeconds(1.5f)
                .build();
        assertThat(telegraphs.dispatch(new PacketCodec.Frame(
                        new PacketHeader(Opcode.OPCODE_BOSS_TELEGRAPH_EVENT_VALUE, ProtocolVersion.CURRENT, 0),
                        event.toByteArray())))
                .isTrue();

        telegraphs.update(1f);
        assertThat(telegraphs.activeRings()).isEqualTo(1);
        telegraphs.update(0.6f);
        assertThat(telegraphs.activeRings()).isZero();
    }

    @Test
    void shouldIgnoreBrokenRings_andCapAnAbsurdDuration() {
        telegraphs.add(Float.NaN, 0f, 4f, 1f);
        telegraphs.add(0f, 0f, -1f, 1f);
        telegraphs.add(0f, 0f, 4f, 0f);
        assertThat(telegraphs.activeRings()).isZero();

        telegraphs.add(0f, 0f, 4f, 600f);
        telegraphs.update(10.1f);
        assertThat(telegraphs.activeRings()).isZero();
    }
}
