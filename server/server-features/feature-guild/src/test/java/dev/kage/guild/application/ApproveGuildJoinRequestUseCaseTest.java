package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.guild.domain.GuildJoinRequest;
import org.junit.jupiter.api.Test;

class ApproveGuildJoinRequestUseCaseTest {

    @Test
    void shouldApprove_andAddMember_whenCallerIsLeader() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        GuildJoinRequest request = port.sendJoinRequest(port.findByName("Konoha").orElseThrow().id(), 5);
        ApproveGuildJoinRequestUseCase useCase = new ApproveGuildJoinRequestUseCase(port);

        ApproveGuildJoinRequestUseCase.Result result = useCase.execute(request.id(), 1);

        assertThat(result).isEqualTo(ApproveGuildJoinRequestUseCase.Result.OK);
        assertThat(port.findMembershipOf(5)).isPresent();
        assertThat(port.findPendingRequestOfCharacter(5)).isEmpty();
    }

    @Test
    void shouldReject_whenCallerIsNotLeader() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        GuildJoinRequest request = port.sendJoinRequest(port.findByName("Konoha").orElseThrow().id(), 5);
        ApproveGuildJoinRequestUseCase useCase = new ApproveGuildJoinRequestUseCase(port);

        ApproveGuildJoinRequestUseCase.Result result = useCase.execute(request.id(), 999);

        assertThat(result).isEqualTo(ApproveGuildJoinRequestUseCase.Result.NOT_FOUND);
        assertThat(port.findMembershipOf(5)).isEmpty();
    }

    @Test
    void shouldReturnNotFound_whenRequestDoesNotExist() {
        ApproveGuildJoinRequestUseCase useCase = new ApproveGuildJoinRequestUseCase(new FakeGuildPort());

        ApproveGuildJoinRequestUseCase.Result result = useCase.execute(999, 1);

        assertThat(result).isEqualTo(ApproveGuildJoinRequestUseCase.Result.NOT_FOUND);
    }

    @Test
    void shouldReturnTargetAlreadyInGuild_andDropStaleRequest_whenApplicantJoinedElsewhereMeanwhile() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        GuildJoinRequest request = port.sendJoinRequest(port.findByName("Konoha").orElseThrow().id(), 5);
        port.createGuild("Suna", 5); // nhân vật 5 tự tạo bang khác trong lúc đơn còn PENDING
        ApproveGuildJoinRequestUseCase useCase = new ApproveGuildJoinRequestUseCase(port);

        ApproveGuildJoinRequestUseCase.Result result = useCase.execute(request.id(), 1);

        assertThat(result).isEqualTo(ApproveGuildJoinRequestUseCase.Result.TARGET_ALREADY_IN_GUILD);
        assertThat(port.findPendingRequestOfCharacter(5)).isEmpty();
    }
}
