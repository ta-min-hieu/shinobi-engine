package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CreatePartyUseCaseTest {

    @Test
    void shouldCreateParty_andMakeCreatorLeader() {
        FakePartyPort port = new FakePartyPort();
        CreatePartyUseCase useCase = new CreatePartyUseCase(port);

        CreatePartyUseCase.Result result = useCase.execute(5);

        assertThat(result.outcome()).isEqualTo(CreatePartyUseCase.Outcome.OK);
        assertThat(port.findMembershipOf(5).orElseThrow().role().name()).isEqualTo("LEADER");
    }

    @Test
    void shouldReject_whenCharacterAlreadyInParty() {
        FakePartyPort port = new FakePartyPort();
        port.createParty(5);
        CreatePartyUseCase useCase = new CreatePartyUseCase(port);

        CreatePartyUseCase.Result result = useCase.execute(5);

        assertThat(result.outcome()).isEqualTo(CreatePartyUseCase.Outcome.ALREADY_IN_PARTY);
    }
}
