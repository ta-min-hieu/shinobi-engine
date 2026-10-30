package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BuyOrderCommandsTest {

    @Test
    void everyDomainOutcomeShouldHaveAWireValue() {
        for (dev.kage.market.domain.BuyOrderOutcome outcome : dev.kage.market.domain.BuyOrderOutcome.values()) {
            assertThat(BuyOrderCommands.wire(outcome).name()).endsWith(outcome.name());
        }
    }
}
