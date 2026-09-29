package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.party.domain.PartyMember;

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

    /**
     * Chỉ verify khúc DỊCH {@code DuplicatePartyMembershipException} → outcome; bản thân race đã
     * verify bằng Postgres thật ở {@code PartyMembershipRaceIntegrationTest}.
     */
    @Test
    void shouldReturnAlreadyInParty_whenPortDetectsRaceAtCreateParty() {
        FakePartyPort port = new StaleMembershipCheckPort();
        port.createParty(5);
        CreatePartyUseCase useCase = new CreatePartyUseCase(port);

        CreatePartyUseCase.Result result = useCase.execute(5);

        assertThat(result.outcome()).isEqualTo(CreatePartyUseCase.Outcome.ALREADY_IN_PARTY);
        assertThat(result.party()).isNull();
    }

    /** Check "đã ở party chưa" luôn thấy rỗng — như transaction bên kia chưa commit lúc kiểm. */
    private static final class StaleMembershipCheckPort extends FakePartyPort {

        @Override
        public Optional<PartyMember> findMembershipOf(long characterId) {
            return Optional.empty();
        }
    }
}
