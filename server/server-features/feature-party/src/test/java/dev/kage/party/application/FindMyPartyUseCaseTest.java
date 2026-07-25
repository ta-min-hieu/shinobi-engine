package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.party.domain.PartyRole;
import org.junit.jupiter.api.Test;

class FindMyPartyUseCaseTest {

    @Test
    void shouldReturnPartyInfo_whenCharacterIsLeader() {
        FakePartyPort port = new FakePartyPort();
        port.createParty(1);
        FindMyPartyUseCase useCase = new FindMyPartyUseCase(port);

        FindMyPartyUseCase.Result result = useCase.execute(1).orElseThrow();

        assertThat(result.myRole()).isEqualTo(PartyRole.LEADER);
    }

    @Test
    void shouldReturnEmpty_whenNotInAnyParty() {
        FindMyPartyUseCase useCase = new FindMyPartyUseCase(new FakePartyPort());

        assertThat(useCase.execute(1)).isEmpty();
    }
}
