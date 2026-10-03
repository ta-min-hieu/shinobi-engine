package dev.kage.tools.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;

final class FailureCountersTest {

    @Test
    void shouldSeparateTimeoutFromMissingResponseFromLostConnection() {
        assertThat(FailureCounters.classifyInWorld(new SocketTimeoutException("Read timed out")))
                .isEqualTo(FailureCounters.Kind.TIMEOUT);
        assertThat(FailureCounters.classifyInWorld(new RoundTrip.ResponseMissingException("no response")))
                .isEqualTo(FailureCounters.Kind.RESPONSE_MISSING);
        assertThat(FailureCounters.classifyInWorld(new EOFException())).isEqualTo(FailureCounters.Kind.CONNECTION_LOST);
        assertThat(FailureCounters.classifyInWorld(new IOException("Connection reset")))
                .isEqualTo(FailureCounters.Kind.CONNECTION_LOST);
    }

    @Test
    void shouldCountEachKindIndependently() {
        FailureCounters counters = new FailureCounters();
        counters.increment(FailureCounters.Kind.HANDSHAKE);
        counters.increment(FailureCounters.Kind.TIMEOUT);
        counters.increment(FailureCounters.Kind.TIMEOUT);

        assertThat(counters.get(FailureCounters.Kind.HANDSHAKE)).isEqualTo(1);
        assertThat(counters.get(FailureCounters.Kind.TIMEOUT)).isEqualTo(2);
        assertThat(counters.get(FailureCounters.Kind.CONNECTION_LOST)).isZero();
        assertThat(counters.total()).isEqualTo(3);
        assertThat(counters).hasToString("handshake=1 timeout=2 response_missing=0 connection_lost=0 rejected=0");
    }
}
