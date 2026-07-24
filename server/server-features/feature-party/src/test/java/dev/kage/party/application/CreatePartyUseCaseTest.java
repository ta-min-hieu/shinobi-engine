package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreatePartyUseCaseTest {

    @Test
    void shouldCreateParty_andMakeCreatorLeader() {
        var port = new FakePartyPort();
        var useCase = new CreatePartyUseCase(port);

        var result = useCase.execute(5);

        assertThat(result.outcome()).isEqualTo(CreatePartyUseCase.Outcome.OK);
        assertThat(port.findMembershipOf(5).orElseThrow().role().name()).isEqualTo("LEADER");
    }

    @Test
    void shouldReject_whenCharacterAlreadyInParty() {
        var port = new FakePartyPort();
        port.createParty(5);
        var useCase = new CreatePartyUseCase(port);

        var result = useCase.execute(5);

        assertThat(result.outcome()).isEqualTo(CreatePartyUseCase.Outcome.ALREADY_IN_PARTY);
    }
}
