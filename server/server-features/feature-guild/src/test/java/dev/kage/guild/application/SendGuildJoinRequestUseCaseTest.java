package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.guild.domain.GuildJoinRequest;

class SendGuildJoinRequestUseCaseTest {

    @Test
    void shouldSendRequest_whenGuildExistsAndCharacterFree() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        SendGuildJoinRequestUseCase useCase = new SendGuildJoinRequestUseCase(port);

        SendGuildJoinRequestUseCase.Result result = useCase.execute(5, "Konoha");

        assertThat(result).isEqualTo(SendGuildJoinRequestUseCase.Result.OK);
        assertThat(port.findPendingRequestOfCharacter(5)).isPresent();
    }

    /** README dòng 42 (2026-10-14): tên bang lưu NFC, người gõ dấu tách rời vẫn xin vào được. */
    @Test
    void shouldFindTheGuild_whenNameIsTypedWithCombiningMarks() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("T\u00F4ng", 1);
        SendGuildJoinRequestUseCase useCase = new SendGuildJoinRequestUseCase(port);

        SendGuildJoinRequestUseCase.Result result = useCase.execute(5, "To\u0302ng");

        assertThat(result).isEqualTo(SendGuildJoinRequestUseCase.Result.OK);
    }

    @Test
    void shouldReject_whenGuildNotFound() {
        SendGuildJoinRequestUseCase useCase = new SendGuildJoinRequestUseCase(new FakeGuildPort());

        SendGuildJoinRequestUseCase.Result result = useCase.execute(5, "KhongTonTai");

        assertThat(result).isEqualTo(SendGuildJoinRequestUseCase.Result.GUILD_NOT_FOUND);
    }

    /** Bug 2026-10-02: tên chứa U+0000 từng tới câu SELECT, Postgres từ chối, client nhận 500. */
    @Test
    void shouldReportGuildNotFound_whenNameContainsNul() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        SendGuildJoinRequestUseCase useCase = new SendGuildJoinRequestUseCase(port);

        SendGuildJoinRequestUseCase.Result result = useCase.execute(5, "Kono\0ha");

        assertThat(result).isEqualTo(SendGuildJoinRequestUseCase.Result.GUILD_NOT_FOUND);
    }

    /**
     * Bug 2026-10-03: leader giải tán bang đúng giữa lúc tra bang theo tên và lúc ghi đơn — vi phạm
     * FOREIGN KEY từng thành 500 {@code error.server_internal}. Bang không còn ⇒ "không tìm thấy".
     */
    @Test
    void shouldReportGuildNotFound_whenGuildIsDisbandedBetweenLookupAndWrite() {
        FakeGuildPort port = new FakeGuildPort() {
            @Override
            public GuildJoinRequest sendJoinRequest(long guildId, long characterId) {
                disbandGuild(1);
                return super.sendJoinRequest(guildId, characterId);
            }
        };
        port.createGuild("Konoha", 1);
        SendGuildJoinRequestUseCase useCase = new SendGuildJoinRequestUseCase(port);

        SendGuildJoinRequestUseCase.Result result = useCase.execute(5, "Konoha");

        assertThat(result).isEqualTo(SendGuildJoinRequestUseCase.Result.GUILD_NOT_FOUND);
        assertThat(port.findPendingRequestOfCharacter(5)).isEmpty();
    }

    @Test
    void shouldReject_whenCharacterAlreadyInAGuild() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        port.createGuild("Suna", 5);
        SendGuildJoinRequestUseCase useCase = new SendGuildJoinRequestUseCase(port);

        SendGuildJoinRequestUseCase.Result result = useCase.execute(5, "Konoha");

        assertThat(result).isEqualTo(SendGuildJoinRequestUseCase.Result.ALREADY_IN_GUILD);
    }

    @Test
    void shouldReject_whenRequestAlreadyPending() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 1);
        SendGuildJoinRequestUseCase useCase = new SendGuildJoinRequestUseCase(port);
        useCase.execute(5, "Konoha");

        SendGuildJoinRequestUseCase.Result result = useCase.execute(5, "Konoha");

        assertThat(result).isEqualTo(SendGuildJoinRequestUseCase.Result.REQUEST_ALREADY_PENDING);
    }
}
