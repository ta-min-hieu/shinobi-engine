package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.ApproveGuildJoinRequestOutcome;
import dev.kage.protocol.CreateGuildOutcome;
import dev.kage.protocol.DisbandGuildOutcome;
import dev.kage.protocol.GetMyGuildResponse;
import dev.kage.protocol.GuildMemberView;
import dev.kage.protocol.KickGuildMemberOutcome;
import dev.kage.protocol.LeaveGuildOutcome;
import dev.kage.protocol.ListGuildMembersResponse;
import dev.kage.protocol.ListPendingGuildJoinRequestsResponse;
import dev.kage.protocol.PendingGuildJoinRequestView;
import dev.kage.protocol.RejectGuildJoinRequestOutcome;
import dev.kage.protocol.SendGuildJoinRequestOutcome;

/**
 * {@link GuildController} thuần Java, không libGDX — chạy được thẳng trong JUnit như
 * {@code ChatControllerTest}.
 */
class GuildControllerTest {

    private final GuildController controller = new GuildController();

    @Test
    @DisplayName("Chưa vào bang nào: tên bang rỗng, hai danh sách rỗng")
    void startsWithoutGuild() {
        assertThat(controller.inGuild()).isFalse();
        assertThat(controller.guildName()).isEmpty();
        assertThat(controller.isLeader()).isFalse();
        assertThat(controller.members()).isEmpty();
        assertThat(controller.pendingRequests()).isEmpty();
    }

    @Test
    @DisplayName("GetMyGuildResponse là nguồn DUY NHẤT của trạng thái 'đang ở bang nào'")
    void adoptsServerGuildState() {
        controller.onMyGuild(GetMyGuildResponse.newBuilder()
                .setInGuild(true)
                .setGuildId(42L)
                .setGuildName("Bạch Hổ")
                .setIsLeader(true)
                .build());

        assertThat(controller.inGuild()).isTrue();
        assertThat(controller.guildId()).isEqualTo(42L);
        assertThat(controller.guildName()).contains("Bạch Hổ");
        assertThat(controller.isLeader()).isTrue();
    }

    @Test
    @DisplayName("Server nói không còn ở bang nào thì XOÁ sạch cả hai danh sách")
    void clearsListsWhenServerSaysNoGuild() {
        controller.onMyGuild(inGuild("Bạch Hổ", true));
        controller.onMembers(members(member(1L, "Kage", true, true)));
        controller.onPendingRequests(pending(request(7L, 9L, "Xin Vao")));

        // Bị đuổi khỏi bang là chuyện client chỉ biết khi HỎI LẠI — guild.proto không có event nào
        // báo. Lúc biết rồi thì không được để danh sách cũ treo lại trên màn hình.
        controller.onMyGuild(GetMyGuildResponse.newBuilder().setInGuild(false).build());

        assertThat(controller.inGuild()).isFalse();
        assertThat(controller.guildName()).isEmpty();
        assertThat(controller.guildId()).isZero();
        assertThat(controller.isLeader()).isFalse();
        assertThat(controller.members()).isEmpty();
        assertThat(controller.pendingRequests()).isEmpty();
    }

    @Test
    @DisplayName("Danh sách thành viên THAY hoàn toàn, không trộn với bản cũ")
    void replacesMembersInsteadOfMerging() {
        controller.onMembers(members(member(1L, "Kage", true, true), member(2L, "Ninja", false, false)));
        controller.onMembers(members(member(1L, "Kage", true, true)));

        assertThat(controller.members()).containsExactly(new GuildController.Member(1L, "Kage", true, true));
    }

    @Test
    @DisplayName("Thành viên giữ nguyên thứ tự và đủ 4 trường của GuildMemberView")
    void keepsMemberFields() {
        controller.onMembers(members(member(1L, "Kage", true, true), member(2L, "Ninja", false, false)));

        List<GuildController.Member> all = controller.members();
        assertThat(all).hasSize(2);
        assertThat(all.get(0)).isEqualTo(new GuildController.Member(1L, "Kage", true, true));
        assertThat(all.get(1)).isEqualTo(new GuildController.Member(2L, "Ninja", false, false));
    }

    @Test
    @DisplayName("Đơn xin gia nhập giữ request_id — thứ duy nhất gửi lại được cho server")
    void keepsRequestId() {
        controller.onPendingRequests(pendingOf(7L, 9L, "Xin Vao"));

        assertThat(controller.pendingRequests()).containsExactly(new GuildController.PendingRequest(7L, 9L, "Xin Vao"));
    }

    @Test
    @DisplayName("Tên bang và tên thành viên do người khác đặt đều đi qua sanitize")
    void sanitizesPlayerAuthoredText() {
        controller.onMyGuild(inGuild("Bang世", true));
        controller.onMembers(members(member(1L, "Ninja界", true, true)));
        controller.onPendingRequests(pendingOf(7L, 9L, "Xin😀"));

        assertThat(controller.guildName()).contains("Bang?");
        assertThat(controller.members().get(0).name()).isEqualTo("Ninja?");
        assertThat(controller.pendingRequests().get(0).characterName()).isEqualTo("Xin??");
    }

    @Test
    @DisplayName("Ô nhập rỗng/toàn khoảng trắng không gửi gì và cũng không báo lỗi")
    void ignoresBlankName() {
        assertThat(controller.composeGuildName("")).isEqualTo(GuildController.ComposeResult.Nothing.INSTANCE);
        assertThat(controller.composeGuildName("    ")).isEqualTo(GuildController.ComposeResult.Nothing.INSTANCE);
    }

    @Test
    @DisplayName("Tên bang được cắt khoảng trắng thừa trước khi gửi")
    void trimsName() {
        assertThat(controller.composeGuildName("  Bạch Hổ  "))
                .isEqualTo(new GuildController.ComposeResult.Send("Bạch Hổ"));
    }

    @Test
    @DisplayName("Tên dài hơn cột VARCHAR(32) bị chặn tại client — server KHÔNG kiểm hộ")
    void rejectsTooLongName() {
        String tooLong = "x".repeat(GuildController.MAX_NAME_LENGTH + 1);

        assertThat(controller.composeGuildName(tooLong))
                .isEqualTo(new GuildController.ComposeResult.Reject("guild.error.name_too_long"));
    }

    @Test
    @DisplayName("Đúng trần MAX_NAME_LENGTH thì vẫn gửi được")
    void acceptsNameAtExactLimit() {
        String exact = "x".repeat(GuildController.MAX_NAME_LENGTH);

        assertThat(controller.composeGuildName(exact)).isEqualTo(new GuildController.ComposeResult.Send(exact));
    }

    @Test
    @DisplayName("Mọi outcome THẬT của cả 7 lệnh đều có khoá i18n, không sót cái nào")
    void mapsEveryRealOutcomeOfEveryCommand() {
        assertEveryRealOutcomeMapped(CreateGuildOutcome.values(), GuildController::createMessageKey);
        assertEveryRealOutcomeMapped(SendGuildJoinRequestOutcome.values(), GuildController::joinMessageKey);
        assertEveryRealOutcomeMapped(ApproveGuildJoinRequestOutcome.values(), GuildController::approveMessageKey);
        assertEveryRealOutcomeMapped(RejectGuildJoinRequestOutcome.values(), GuildController::rejectMessageKey);
        assertEveryRealOutcomeMapped(LeaveGuildOutcome.values(), GuildController::leaveMessageKey);
        assertEveryRealOutcomeMapped(DisbandGuildOutcome.values(), GuildController::disbandMessageKey);
        assertEveryRealOutcomeMapped(KickGuildMemberOutcome.values(), GuildController::kickMessageKey);
    }

    /**
     * Outcome THẬT (số > 0) phải có khoá; {@code UNSPECIFIED} (số 0, protobuf bắt buộc có nhưng
     * guild.proto ghi rõ "KHÔNG dùng cho kết quả thật") và {@code UNRECOGNIZED} (server mới hơn
     * client) phải RỖNG — đó là cách chỗ gọi phân biệt "không biết nói gì" với "quên map".
     */
    private static <T extends Enum<T>> void assertEveryRealOutcomeMapped(
            T[] outcomes, Function<T, Optional<String>> lookup) {
        for (T outcome : outcomes) {
            boolean real =
                    !"UNRECOGNIZED".equals(outcome.name()) && !outcome.name().endsWith("_UNSPECIFIED");
            assertThat(lookup.apply(outcome))
                    .as("%s.%s", outcome.getDeclaringClass().getSimpleName(), outcome.name())
                    .matches(key -> key.isPresent() == real);
        }
    }

    @Test
    @DisplayName("Tên bang sai luật tới bằng ErrorResponse (2026-09-29) — khoá thật có câu, khoá lạ thì rỗng")
    void mapsGuildNameInvalidErrorKey() {
        assertThat(GuildController.createErrorMessageKey("error.guild_name_invalid"))
                .contains("error.guild_name_invalid");
        assertThat(GuildController.createErrorMessageKey("error.some_future_key"))
                .isEmpty();
    }

    @Test
    @DisplayName("Outcome thật có khoá; UNSPECIFIED/UNRECOGNIZED thì rỗng để chỗ gọi tự nói 'không hiểu'")
    void mapsRealOutcomesAndLeavesUnknownEmpty() {
        assertThat(GuildController.createMessageKey(CreateGuildOutcome.CREATE_GUILD_OUTCOME_NAME_TAKEN))
                .contains("guild.error.name_taken");
        assertThat(GuildController.createMessageKey(CreateGuildOutcome.CREATE_GUILD_OUTCOME_UNSPECIFIED))
                .isEmpty();
        assertThat(GuildController.createMessageKey(CreateGuildOutcome.UNRECOGNIZED))
                .isEmpty();

        assertThat(GuildController.joinMessageKey(
                        SendGuildJoinRequestOutcome.SEND_GUILD_JOIN_REQUEST_OUTCOME_REQUEST_ALREADY_PENDING))
                .contains("guild.error.request_already_pending");
        assertThat(GuildController.approveMessageKey(
                        ApproveGuildJoinRequestOutcome.APPROVE_GUILD_JOIN_REQUEST_OUTCOME_TARGET_ALREADY_IN_GUILD))
                .contains("guild.error.target_already_in_guild");
        assertThat(GuildController.rejectMessageKey(
                        RejectGuildJoinRequestOutcome.REJECT_GUILD_JOIN_REQUEST_OUTCOME_NOT_FOUND))
                .contains("guild.error.request_not_found");
        assertThat(GuildController.leaveMessageKey(LeaveGuildOutcome.LEAVE_GUILD_OUTCOME_LEADER_CANNOT_LEAVE))
                .contains("guild.error.leader_cannot_leave");
        assertThat(GuildController.disbandMessageKey(DisbandGuildOutcome.DISBAND_GUILD_OUTCOME_NOT_LEADER))
                .contains("guild.error.not_leader");
        assertThat(GuildController.kickMessageKey(KickGuildMemberOutcome.KICK_GUILD_MEMBER_OUTCOME_CANNOT_KICK_SELF))
                .contains("guild.error.cannot_kick_self");
    }

    @Test
    @DisplayName("clear() xoá sạch mọi thứ — trạng thái bang không sống sót sang phiên chơi sau")
    void clearResetsEverything() {
        controller.onMyGuild(inGuild("Bạch Hổ", true));
        controller.onMembers(members(member(1L, "Kage", true, true)));
        controller.onPendingRequests(pendingOf(7L, 9L, "Xin Vao"));

        controller.clear();

        assertThat(controller.inGuild()).isFalse();
        assertThat(controller.guildName()).isEmpty();
        assertThat(controller.members()).isEmpty();
        assertThat(controller.pendingRequests()).isEmpty();
    }

    private static GetMyGuildResponse inGuild(String name, boolean leader) {
        return GetMyGuildResponse.newBuilder()
                .setInGuild(true)
                .setGuildId(42L)
                .setGuildName(name)
                .setIsLeader(leader)
                .build();
    }

    private static GuildMemberView member(long characterId, String name, boolean leader, boolean online) {
        return GuildMemberView.newBuilder()
                .setCharacterId(characterId)
                .setName(name)
                .setIsLeader(leader)
                .setOnline(online)
                .build();
    }

    private static ListGuildMembersResponse members(GuildMemberView... views) {
        ListGuildMembersResponse.Builder builder = ListGuildMembersResponse.newBuilder();
        for (GuildMemberView view : views) {
            builder.addMembers(view);
        }
        return builder.build();
    }

    private static PendingGuildJoinRequestView request(long requestId, long characterId, String name) {
        return PendingGuildJoinRequestView.newBuilder()
                .setRequestId(requestId)
                .setCharacterId(characterId)
                .setCharacterName(name)
                .build();
    }

    private static ListPendingGuildJoinRequestsResponse pending(PendingGuildJoinRequestView... views) {
        ListPendingGuildJoinRequestsResponse.Builder builder = ListPendingGuildJoinRequestsResponse.newBuilder();
        for (PendingGuildJoinRequestView view : views) {
            builder.addRequests(view);
        }
        return builder.build();
    }

    private static ListPendingGuildJoinRequestsResponse pendingOf(long requestId, long characterId, String name) {
        return pending(request(requestId, characterId, name));
    }
}
