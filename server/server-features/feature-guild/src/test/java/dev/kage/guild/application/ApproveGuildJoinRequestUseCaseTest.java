package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApproveGuildJoinRequestUseCaseTest {

    @Test
    void shouldApprove_andAddMember_whenCallerIsLeader() {
        var port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        var request = port.sendJoinRequest(port.findByName("Konoha").orElseThrow().id(), 5);
        var useCase = new ApproveGuildJoinRequestUseCase(port);

        var result = useCase.execute(request.id(), 1);

        assertThat(result).isEqualTo(ApproveGuildJoinRequestUseCase.Result.OK);
        assertThat(port.findMembershipOf(5)).isPresent();
        assertThat(port.findPendingRequestOfCharacter(5)).isEmpty();
    }

    @Test
    void shouldReject_whenCallerIsNotLeader() {
        var port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        var request = port.sendJoinRequest(port.findByName("Konoha").orElseThrow().id(), 5);
        var useCase = new ApproveGuildJoinRequestUseCase(port);

        var result = useCase.execute(request.id(), 999);

        assertThat(result).isEqualTo(ApproveGuildJoinRequestUseCase.Result.NOT_FOUND);
        assertThat(port.findMembershipOf(5)).isEmpty();
    }

    @Test
    void shouldReturnNotFound_whenRequestDoesNotExist() {
        var useCase = new ApproveGuildJoinRequestUseCase(new FakeGuildPort());

        var result = useCase.execute(999, 1);

        assertThat(result).isEqualTo(ApproveGuildJoinRequestUseCase.Result.NOT_FOUND);
    }

    @Test
    void shouldReturnTargetAlreadyInGuild_andDropStaleRequest_whenApplicantJoinedElsewhereMeanwhile() {
        var port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        var request = port.sendJoinRequest(port.findByName("Konoha").orElseThrow().id(), 5);
        port.createGuild("Suna", 5); // nhân vật 5 tự tạo bang khác trong lúc đơn còn PENDING
        var useCase = new ApproveGuildJoinRequestUseCase(port);

        var result = useCase.execute(request.id(), 1);

        assertThat(result).isEqualTo(ApproveGuildJoinRequestUseCase.Result.TARGET_ALREADY_IN_GUILD);
        assertThat(port.findPendingRequestOfCharacter(5)).isEmpty();
    }
}
