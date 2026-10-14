package dev.kage.guild.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.guild.domain.DuplicateGuildMembershipException;
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildMember;
import dev.kage.guild.domain.GuildPort;

class CreateGuildUseCaseTest {

    @Test
    void shouldCreateGuild_andMakeCreatorLeader() {
        FakeGuildPort port = new FakeGuildPort();
        CreateGuildUseCase useCase = new CreateGuildUseCase(port);

        CreateGuildUseCase.Result result = useCase.execute(5, "Konoha");

        assertThat(result.outcome()).isEqualTo(CreateGuildUseCase.Outcome.OK);
        assertThat(result.guild().name()).isEqualTo("Konoha");
        assertThat(port.findMembershipOf(5).orElseThrow().role().name()).isEqualTo("LEADER");
    }

    /**
     * Quyết định chủ dự án 2026-09-29: tên bang theo đúng luật tên nhân vật. Tên sai bị từ chối
     * TRƯỚC khi chạm Port — không bang nào được tạo, người gửi không thành trưởng bang nào.
     */
    @Test
    void shouldRejectWithoutCreatingAnything_whenNameIsInvalid() {
        FakeGuildPort port = new FakeGuildPort();
        CreateGuildUseCase useCase = new CreateGuildUseCase(port);

        for (String name : java.util.List.of("", "A", "Thanh Vân Môn", "Kiếm-Tông", "ABCDEFGHIJKLMNOPQ")) {
            CreateGuildUseCase.Result result = useCase.execute(5, name);

            assertThat(result.outcome()).as("'%s'", name).isEqualTo(CreateGuildUseCase.Outcome.INVALID_NAME);
            assertThat(result.guild()).isNull();
            assertThat(port.findByName(name)).isEmpty();
        }
        assertThat(port.findMembershipOf(5)).isEmpty();
    }

    @Test
    void shouldAcceptDecomposedVietnameseAndStoreItComposed() {
        FakeGuildPort port = new FakeGuildPort();
        CreateGuildUseCase useCase = new CreateGuildUseCase(port);

        // "Kiếm_Tông" với dấu tách rời (NFD) — README dòng 42, 2026-10-14.
        CreateGuildUseCase.Result result = useCase.execute(5, "Kie\u0302\u0301m_To\u0302ng");

        assertThat(result.outcome()).isEqualTo(CreateGuildUseCase.Outcome.OK);
        assertThat(result.guild().name()).isEqualTo("Ki\u1EBFm_T\u00F4ng");
    }

    @Test
    void shouldAcceptVietnameseDiacritics() {
        FakeGuildPort port = new FakeGuildPort();

        CreateGuildUseCase.Result result = new CreateGuildUseCase(port).execute(5, "ThanhVânMôn");

        assertThat(result.outcome()).isEqualTo(CreateGuildUseCase.Outcome.OK);
    }

    @Test
    void shouldReject_whenCharacterAlreadyInGuild() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 5);
        CreateGuildUseCase useCase = new CreateGuildUseCase(port);

        CreateGuildUseCase.Result result = useCase.execute(5, "Suna");

        assertThat(result.outcome()).isEqualTo(CreateGuildUseCase.Outcome.ALREADY_IN_GUILD);
    }

    @Test
    void shouldReject_whenNameAlreadyTaken() {
        FakeGuildPort port = new FakeGuildPort();
        port.createGuild("Konoha", 5);
        CreateGuildUseCase useCase = new CreateGuildUseCase(port);

        CreateGuildUseCase.Result result = useCase.execute(7, "Konoha");

        assertThat(result.outcome()).isEqualTo(CreateGuildUseCase.Outcome.NAME_TAKEN);
    }

    /**
     * {@code findMembershipOf} rồi mới {@code createGuild} không atomic — race THẬT (xác nhận bằng
     * Testcontainers ở {@code GuildIntegrationTest
     * #shouldTranslateRaceIntoDuplicateGuildMembershipException_whenSameCharacterRacesTwoCreateGuildCalls})
     * là 2 lệnh tạo bang gần như đồng thời của CÙNG 1 nhân vật đều vượt qua check đó trước khi 1
     * trong 2 transaction commit; DB (UNIQUE {@code guild_member.character_id}) chặn ở lời gọi
     * {@code createGuild} thay vì check này. Test đơn vị này chỉ verify khúc DỊCH exception →
     * outcome (nhanh, không cần Postgres thật) — bản thân race đã verify bằng Postgres thật ở test
     * kia.
     */
    @Test
    void shouldReturnAlreadyInGuild_whenPortDetectsRaceAtCreateGuild() {
        GuildPort port = new ThrowingOnCreateGuildPort();
        CreateGuildUseCase useCase = new CreateGuildUseCase(port);

        CreateGuildUseCase.Result result = useCase.execute(5, "Konoha");

        assertThat(result.outcome()).isEqualTo(CreateGuildUseCase.Outcome.ALREADY_IN_GUILD);
        assertThat(result.guild()).isNull();
    }

    /** Chỉ mô phỏng đúng 2 method mà {@link CreateGuildUseCase} gọi — xem {@link UnsupportedGuildPort}. */
    private static final class ThrowingOnCreateGuildPort extends UnsupportedGuildPort {

        @Override
        public Optional<GuildMember> findMembershipOf(long characterId) {
            return Optional.empty();
        }

        @Override
        public Guild createGuild(String name, long leaderCharacterId) {
            throw new DuplicateGuildMembershipException(leaderCharacterId);
        }
    }
}
