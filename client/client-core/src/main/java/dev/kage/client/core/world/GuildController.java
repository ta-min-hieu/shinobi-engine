package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.kage.client.core.render.FontFactory;
import dev.kage.protocol.ApproveGuildJoinRequestOutcome;
import dev.kage.protocol.CreateGuildOutcome;
import dev.kage.protocol.DisbandGuildOutcome;
import dev.kage.protocol.DonateToGuildOutcome;
import dev.kage.protocol.GetMyGuildResponse;
import dev.kage.protocol.GuildMemberView;
import dev.kage.protocol.GuildProgressView;
import dev.kage.protocol.KickGuildMemberOutcome;
import dev.kage.protocol.LeaveGuildOutcome;
import dev.kage.protocol.ListGuildMembersResponse;
import dev.kage.protocol.ListPendingGuildJoinRequestsResponse;
import dev.kage.protocol.PendingGuildJoinRequestView;
import dev.kage.protocol.RejectGuildJoinRequestOutcome;
import dev.kage.protocol.SendGuildJoinRequestOutcome;

/**
 * Trạng thái bang hội (2026-08-25) — thuần Java, không libGDX/GL, test được thẳng (UI.md §4:
 * controller giữ trạng thái, widget chỉ vẽ). Cùng hình dạng {@link ChatController}.
 *
 * <p>Giữ đúng ba thứ server có thể trả lời: <b>bang của mình</b>, <b>danh sách thành viên</b>, và
 * <b>đơn xin gia nhập đang chờ</b> — cộng luật biến một chuỗi người chơi gõ thành tên bang hợp lệ
 * ({@link #composeGuildName}).
 *
 * <p><b>⚠️ Khoảng trống của giao thức: guild.proto KHÔNG có một event đẩy nào</b> (kiểm lại
 * opcode_registry.proto: dải 700-719 chỉ toàn cặp Command/Response và Request/Response, không có
 * {@code *Event}). Hệ quả có thật, không phải lý thuyết:
 *
 * <ul>
 *   <li>Bị leader ĐUỔI khỏi bang → client này không nhận được gì, vẫn tưởng mình còn trong bang.
 *   <li>Đơn xin gia nhập được DUYỆT → người xin không nhận được gì, vẫn tưởng đơn còn treo.
 *   <li>Bang bị GIẢI TÁN → mọi thành viên khác không nhận được gì.
 *   <li>Người khác vào/rời bang → danh sách thành viên trên màn hình đứng im.
 * </ul>
 *
 * <p>Cách bù duy nhất mà client được phép làm là <b>hỏi lại server sau MỌI lệnh đổi trạng thái</b>
 * ({@link GuildCoordinator#refresh}) và tuyệt đối KHÔNG tự suy trạng thái mới từ việc "lệnh vừa trả
 * OK" (Điều 15 — server là nguồn chân lý duy nhất). Vì vậy lớp này chỉ có đường ghi trạng thái từ
 * ba response tra cứu; không một hàm {@code onXxxOk()} nào được sửa {@link #inGuild} cả.
 *
 * <p>Tên bang và tên thành viên là chữ do <b>người chơi khác</b> đặt, nên chúng đi qua
 * {@link ChatController#sanitize} y như tin nhắn chat — font UI sinh từ
 * {@link FontFactory#VIETNAMESE_CHARACTERS} là tập ĐÓNG và im lặng nuốt mọi glyph ngoài tập đó.
 */
final class GuildController {

    /**
     * Khớp {@code VARCHAR(32)} của cột {@code guild.name} (V14__create_guild_tables.sql). Nhân đôi
     * hằng số là CÓ CHỦ ĐÍCH, cùng lý do {@link ChatController#MAX_BODY_LENGTH}: client-core không
     * được phụ thuộc module server, và luật thật vẫn nằm ở DB.
     *
     * <p>Từ 2026-09-29 server kiểm tên bang MỚI theo luật tên nhân vật (2-16 ký tự, chữ/số/{@code _},
     * quyết định chủ dự án) và trả {@code ErrorResponse} {@code error.guild_name_invalid} — xem
     * {@link #createErrorMessageKey}. Trần 32 ở đây CỐ Ý không hạ xuống 16: ô nhập này dùng chung cho
     * xin gia nhập, và bang tạo trước ngày đó có thể mang tên dài tới 32 ký tự.
     */
    static final int MAX_NAME_LENGTH = 32;

    /** Khoá lỗi server trả khi tên bang sai luật (= luật tên nhân vật, 2026-09-29). */
    private static final String GUILD_NAME_INVALID = "error.guild_name_invalid";

    /** Một thành viên bang. {@code online} do gateway ghép từ registry, không phải từ feature-guild. */
    record Member(long characterId, String name, boolean leader, boolean online) {}

    /** Một đơn xin gia nhập đang chờ duyệt — chỉ leader mới nhận được danh sách này. */
    record PendingRequest(long requestId, long characterId, String characterName) {}

    /**
     * Kết quả biến chuỗi người chơi gõ thành tên bang. Ba nhánh, {@code sealed} để chỗ gọi không thể
     * quên nhánh nào (/CLAUDE.md §7) — cùng hình dạng {@link ChatController.ComposeResult}.
     */
    sealed interface ComposeResult {

        /** Tên hợp lệ ở mức client, gửi đi được. Server vẫn là nơi quyết định cuối cùng. */
        record Send(String guildName) implements ComposeResult {}

        /** Không gửi, và nói cho người chơi biết vì sao. */
        record Reject(String messageKey) implements ComposeResult {}

        /** Ô nhập rỗng — không gửi, KHÔNG báo lỗi (bấm nhầm nút không đáng bị mắng). */
        enum Nothing implements ComposeResult {
            INSTANCE
        }
    }

    private boolean inGuild;

    private long guildId;

    private String guildName = "";

    private boolean leader;

    private final List<Member> members = new ArrayList<>();

    private final List<PendingRequest> pendingRequests = new ArrayList<>();

    /** ADR-068 — cống hiến theo thành viên của lần liệt kê gần nhất. */
    private final Map<Long, Long> contributions = new HashMap<>();

    /** ADR-068/069 — cấp bang + nhiệm vụ tuần, theo lời server (mặc định rỗng = chưa biết). */
    private GuildProgressView progress = GuildProgressView.getDefaultInstance();

    /**
     * Bang của mình, theo lời server. Đây là đường DUY NHẤT trạng thái "đang ở trong bang" thay đổi —
     * xem javadoc lớp về việc vì sao không có {@code onCreateOk()}.
     *
     * <p>Không ở bang nào thì XOÁ luôn cả hai danh sách: vừa bị đuổi mà màn hình còn treo danh sách
     * thành viên của bang cũ là nói dối người chơi bằng dữ liệu cũ.
     */
    void onMyGuild(GetMyGuildResponse response) {
        inGuild = response.getInGuild();
        progress = response.getProgress();
        if (!inGuild) {
            guildId = 0L;
            guildName = "";
            leader = false;
            members.clear();
            pendingRequests.clear();
            return;
        }
        guildId = response.getGuildId();
        guildName = ChatController.sanitize(response.getGuildName());
        leader = response.getIsLeader();
    }

    /** Danh sách thành viên mới nhất từ server — thay TOÀN BỘ, không trộn với bản cũ. */
    void onMembers(ListGuildMembersResponse response) {
        members.clear();
        contributions.clear();
        for (GuildMemberView view : response.getMembersList()) {
            contributions.put(view.getCharacterId(), view.getContribution());
            members.add(new Member(
                    view.getCharacterId(),
                    ChatController.sanitize(view.getName()),
                    view.getIsLeader(),
                    view.getOnline()));
        }
    }

    /**
     * Đơn đang chờ duyệt. Server trả RỖNG cho người không phải leader
     * ({@code listPendingJoinRequests}), nên danh sách rỗng KHÔNG có nghĩa là "không ai xin" —
     * {@link #isLeader()} mới là thứ quyết định có bày mục này ra hay không.
     */
    void onPendingRequests(ListPendingGuildJoinRequestsResponse response) {
        pendingRequests.clear();
        for (PendingGuildJoinRequestView view : response.getRequestsList()) {
            pendingRequests.add(new PendingRequest(
                    view.getRequestId(), view.getCharacterId(), ChatController.sanitize(view.getCharacterName())));
        }
    }

    boolean inGuild() {
        return inGuild;
    }

    /**
     * Tên bang của mình, rỗng khi chưa ở bang nào — {@code guild_id}/{@code guild_name} trong
     * {@code GetMyGuildResponse} "chỉ có ý nghĩa khi in_guild = true" (guild.proto), nên trả
     * {@link Optional} thay vì một chuỗi rỗng mà chỗ gọi có thể quên kiểm.
     */
    Optional<String> guildName() {
        return inGuild ? Optional.of(guildName) : Optional.empty();
    }

    /** Id bang của mình — 0 khi chưa ở bang nào (xem {@link #guildName()} về ý nghĩa của field này). */
    long guildId() {
        return guildId;
    }

    boolean isLeader() {
        return leader;
    }

    /** Thành viên, đúng thứ tự server trả về. */
    List<Member> members() {
        return List.copyOf(members);
    }

    /** Đơn đang chờ, đúng thứ tự server trả về. */
    List<PendingRequest> pendingRequests() {
        return List.copyOf(pendingRequests);
    }

    /**
     * Biến chuỗi trong ô nhập thành tên bang gửi đi được. Dùng chung cho CẢ tạo bang lẫn xin gia
     * nhập: hai lệnh khác nhau nhưng cùng nhận đúng một thứ — một cái tên bang (guild.proto:
     * {@code CreateGuildCommand.name} và {@code SendGuildJoinRequestCommand.guild_name}).
     *
     * @param raw đúng những gì đang nằm trong ô nhập
     * @return xem {@link ComposeResult}
     */
    ComposeResult composeGuildName(String raw) {
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return ComposeResult.Nothing.INSTANCE;
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            return new ComposeResult.Reject("guild.error.name_too_long");
        }
        return new ComposeResult.Send(trimmed);
    }

    /** Rời màn hình/mất kết nối — trạng thái bang của một phiên chơi không được sống sót sang phiên sau. */
    void clear() {
        inGuild = false;
        guildId = 0L;
        guildName = "";
        leader = false;
        members.clear();
        pendingRequests.clear();
        contributions.clear();
        progress = GuildProgressView.getDefaultInstance();
    }

    // ---------------------------------------------------------------------------------------------
    // Bảng tra khoá i18n cho từng outcome.
    //
    // Bảng tra thay cho `switch` phủ hết enum — cùng cách GatewayChannelHandler làm với các bảng
    // *_OUTCOME_MAPPING của nó, và cùng lý do ChatController.OUTCOME_MESSAGE_KEYS: một biểu thức
    // switch phủ hết một enum protobuf tự nó đã vượt trần CyclomaticComplexity của PMD.
    //
    // UNSPECIFIED và UNRECOGNIZED CỐ TÌNH vắng mặt ở mọi bảng: cả hai rơi vào Optional.empty(), và
    // chỗ gọi dịch nó thành "guild.error.unknown". Cái sau là kết quả của một server MỚI HƠN client
    // — không thể liệt kê trước, chỉ có thể nói ra là không hiểu.
    //
    // ⚠️ Khác chat, OK ở đây CÓ khoá riêng chứ không im lặng: guild.proto không có event nào dội
    // lại việc vừa xảy ra (xem javadoc lớp), nên nếu client không tự nói "đã tạo bang" thì người
    // chơi chỉ thấy một cái nút vừa bấm xong mà không có gì đổi.
    // ---------------------------------------------------------------------------------------------

    private static final Map<CreateGuildOutcome, String> CREATE_MESSAGE_KEYS = Map.of(
            CreateGuildOutcome.CREATE_GUILD_OUTCOME_OK, "guild.notice.created",
            CreateGuildOutcome.CREATE_GUILD_OUTCOME_ALREADY_IN_GUILD, "guild.error.already_in_guild",
            CreateGuildOutcome.CREATE_GUILD_OUTCOME_NAME_TAKEN, "guild.error.name_taken");

    private static final Map<SendGuildJoinRequestOutcome, String> JOIN_MESSAGE_KEYS = Map.of(
            SendGuildJoinRequestOutcome.SEND_GUILD_JOIN_REQUEST_OUTCOME_OK,
            "guild.notice.join_requested",
            SendGuildJoinRequestOutcome.SEND_GUILD_JOIN_REQUEST_OUTCOME_ALREADY_IN_GUILD,
            "guild.error.already_in_guild",
            SendGuildJoinRequestOutcome.SEND_GUILD_JOIN_REQUEST_OUTCOME_GUILD_NOT_FOUND,
            "guild.error.guild_not_found",
            SendGuildJoinRequestOutcome.SEND_GUILD_JOIN_REQUEST_OUTCOME_REQUEST_ALREADY_PENDING,
            "guild.error.request_already_pending");

    private static final Map<ApproveGuildJoinRequestOutcome, String> APPROVE_MESSAGE_KEYS = Map.of(
            ApproveGuildJoinRequestOutcome.APPROVE_GUILD_JOIN_REQUEST_OUTCOME_OK,
            "guild.notice.approved",
            ApproveGuildJoinRequestOutcome.APPROVE_GUILD_JOIN_REQUEST_OUTCOME_NOT_FOUND,
            "guild.error.request_not_found",
            ApproveGuildJoinRequestOutcome.APPROVE_GUILD_JOIN_REQUEST_OUTCOME_TARGET_ALREADY_IN_GUILD,
            "guild.error.target_already_in_guild");

    private static final Map<RejectGuildJoinRequestOutcome, String> REJECT_MESSAGE_KEYS = Map.of(
            RejectGuildJoinRequestOutcome.REJECT_GUILD_JOIN_REQUEST_OUTCOME_OK, "guild.notice.rejected",
            RejectGuildJoinRequestOutcome.REJECT_GUILD_JOIN_REQUEST_OUTCOME_NOT_FOUND, "guild.error.request_not_found");

    private static final Map<LeaveGuildOutcome, String> LEAVE_MESSAGE_KEYS = Map.of(
            LeaveGuildOutcome.LEAVE_GUILD_OUTCOME_OK, "guild.notice.left",
            LeaveGuildOutcome.LEAVE_GUILD_OUTCOME_NOT_IN_GUILD, "guild.error.not_in_guild",
            LeaveGuildOutcome.LEAVE_GUILD_OUTCOME_LEADER_CANNOT_LEAVE, "guild.error.leader_cannot_leave");

    private static final Map<DisbandGuildOutcome, String> DISBAND_MESSAGE_KEYS = Map.of(
            DisbandGuildOutcome.DISBAND_GUILD_OUTCOME_OK, "guild.notice.disbanded",
            DisbandGuildOutcome.DISBAND_GUILD_OUTCOME_NOT_LEADER, "guild.error.not_leader");

    private static final Map<KickGuildMemberOutcome, String> KICK_MESSAGE_KEYS = Map.of(
            KickGuildMemberOutcome.KICK_GUILD_MEMBER_OUTCOME_OK, "guild.notice.kicked",
            KickGuildMemberOutcome.KICK_GUILD_MEMBER_OUTCOME_CANNOT_KICK_SELF, "guild.error.cannot_kick_self",
            KickGuildMemberOutcome.KICK_GUILD_MEMBER_OUTCOME_TARGET_NOT_FOUND, "guild.error.target_not_found",
            KickGuildMemberOutcome.KICK_GUILD_MEMBER_OUTCOME_NOT_FOUND, "guild.error.kick_not_allowed");

    /**
     * Khoá i18n của một outcome, hoặc rỗng khi client không nhận ra giá trị đó.
     *
     * <p>Gộp về một hàm generic thay vì bảy hàm giống hệt nhau: mọi enum outcome của guild.proto đều
     * chỉ cần đúng một phép tra bảng, và {@link Map#get} trả {@code null} cho khoá lạ —
     * {@link Optional#ofNullable} là chỗ DUY NHẤT trong luồng này biến {@code null} đó thành thứ
     * chỗ gọi không thể quên xử lý (/CLAUDE.md §11: không trả {@code null}).
     */
    private static <T> Optional<String> messageKey(Map<T, String> table, T outcome) {
        return Optional.ofNullable(table.get(outcome));
    }

    /**
     * {@code error_key} server gửi qua {@code ErrorResponse} cho lệnh Tạo bang — hiện chỉ có tên sai
     * luật (quyết định chủ dự án 2026-09-29; guild.proto KHÔNG có outcome cho ca này).
     *
     * @return rỗng khi khoá lạ (server MỚI HƠN client) — chỗ gọi nói "không hiểu", không đoán bừa
     */
    static Optional<String> createErrorMessageKey(String serverErrorKey) {
        return GUILD_NAME_INVALID.equals(serverErrorKey) ? Optional.of(serverErrorKey) : Optional.empty();
    }

    static Optional<String> createMessageKey(CreateGuildOutcome outcome) {
        return messageKey(CREATE_MESSAGE_KEYS, outcome);
    }

    static Optional<String> joinMessageKey(SendGuildJoinRequestOutcome outcome) {
        return messageKey(JOIN_MESSAGE_KEYS, outcome);
    }

    static Optional<String> approveMessageKey(ApproveGuildJoinRequestOutcome outcome) {
        return messageKey(APPROVE_MESSAGE_KEYS, outcome);
    }

    static Optional<String> rejectMessageKey(RejectGuildJoinRequestOutcome outcome) {
        return messageKey(REJECT_MESSAGE_KEYS, outcome);
    }

    static Optional<String> leaveMessageKey(LeaveGuildOutcome outcome) {
        return messageKey(LEAVE_MESSAGE_KEYS, outcome);
    }

    static Optional<String> disbandMessageKey(DisbandGuildOutcome outcome) {
        return messageKey(DISBAND_MESSAGE_KEYS, outcome);
    }

    static Optional<String> kickMessageKey(KickGuildMemberOutcome outcome) {
        return messageKey(KICK_MESSAGE_KEYS, outcome);
    }

    /** Cấp bang + nhiệm vụ tuần mới nhất (ADR-068/069). */
    GuildProgressView progress() {
        return progress;
    }

    /** Cống hiến của một thành viên (0 nếu chưa biết). */
    long contributionOf(long characterId) {
        return contributions.getOrDefault(characterId, 0L);
    }

    /** Khoá i18n cho kết quả góp linh thạch; rỗng nếu client không nhận ra (server mới hơn). */
    static Optional<String> donateMessageKey(DonateToGuildOutcome outcome) {
        return switch (outcome) {
            case DONATE_TO_GUILD_OUTCOME_OK -> Optional.of("guild.notice.donated");
            case DONATE_TO_GUILD_OUTCOME_NOT_IN_GUILD -> Optional.of("guild.error.not_in_guild");
            case DONATE_TO_GUILD_OUTCOME_INSUFFICIENT_GOLD -> Optional.of("guild.error.insufficient_gold");
            case DONATE_TO_GUILD_OUTCOME_INVALID_AMOUNT -> Optional.of("guild.error.invalid_amount");
            case DONATE_TO_GUILD_OUTCOME_DUPLICATE_REQUEST -> Optional.of("guild.notice.donated");
            default -> Optional.empty();
        };
    }
}
