package dev.kage.client.core.world;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import dev.kage.client.core.render.FontFactory;
import dev.kage.protocol.AcceptDuelOutcome;
import dev.kage.protocol.ChallengeDuelOutcome;
import dev.kage.protocol.DeclineDuelOutcome;
import dev.kage.protocol.DuelChallengeReceivedEvent;
import dev.kage.protocol.DuelResultEvent;
import dev.kage.protocol.DuelStartedEvent;
import dev.kage.protocol.ExitDuelOutcome;
import dev.kage.protocol.GetPvpRatingResponse;

/**
 * Trạng thái đấu tay đôi + xếp hạng PvP (2026-08-27) — thuần Java, không libGDX/GL, test được thẳng
 * (UI.md §4: controller giữ trạng thái, widget chỉ vẽ). Cùng hình dạng {@link GuildController}.
 *
 * <p>Giữ đúng những gì server nói ra: lời thách đấu đang chờ mình trả lời, lời thách đấu mình vừa
 * gửi, trận đấu đang diễn ra, và điểm xếp hạng. <b>Không một dòng nào ở đây tự quyết thắng thua hay
 * tự cộng trừ điểm</b> (Điều 15) — {@link #onDuelResult} chỉ ĐỌC lại con số server gửi kèm
 * {@link DuelResultEvent}.
 *
 * <p><b>⚠️ Khoảng trống của giao thức #1 — thách đấu theo TÊN, nhưng người trên màn hình không có
 * tên.</b> {@code ChallengeDuelCommand.target_name} nhận một cái tên, trong khi
 * {@code CharacterSpawnedEvent} (movement.proto) chỉ mang {@code character_id}, {@code faction} và
 * {@code costume_tier} — <b>không có tên</b>. Client vì vậy không thể biến một cú click vào người
 * chơi trên màn hình thành một cái tên gửi đi được, nếu chỉ dựa vào luồng spawn.
 *
 * <p>Cách bù duy nhất trong quyền hạn của client là <b>nhớ lại</b> mọi cặp (id, tên) đã từng đi qua
 * dây ({@link #rememberName}): tin nhắn chat ({@code ChatMessageEvent.sender_id}/
 * {@code sender_name}), danh sách bạn bè/nhóm/bang hội, và chính hai event của arena
 * ({@code DuelChallengeReceivedEvent}, {@code DuelStartedEvent}). Người chưa từng xuất hiện ở nguồn
 * nào trong số đó thì click vào vẫn không thách đấu được — lúc đó chỉ còn cách gõ tay tên vào ô
 * nhập. Đóng lỗ này thật sự cần thêm {@code name} vào {@code CharacterSpawnedEvent}, tức sửa .proto
 * — nằm ngoài quyền của client (/CLAUDE.md §4).
 *
 * <p><b>⚠️ Khoảng trống của giao thức #2 — chỉ giữ được MỘT lời thách đấu đến.</b> arena.proto phát
 * {@code DuelChallengeReceivedEvent} rời rạc và không có cách nào hỏi lại "ai đang thách tôi", nên
 * lớp này chỉ giữ lời MỚI NHẤT. Hai người thách cùng lúc thì lời trước biến khỏi màn hình dù phía
 * server nó vẫn còn — client KHÔNG tự từ chối hộ (từ chối là quyết định của người chơi, không phải
 * của máy).
 *
 * <p>Tên nhân vật là chữ do <b>người khác</b> đặt nên đi qua {@link ChatController#sanitize} y như
 * tin nhắn chat — font UI sinh từ {@link FontFactory#VIETNAMESE_CHARACTERS} là tập ĐÓNG và im lặng
 * nuốt mọi glyph ngoài tập đó. Vì vậy {@link Player} giữ CẢ HAI tên, cùng lý do
 * {@code FriendController.Friend}: bản đã sanitize để hiện ra, bản thô để gửi lên server.
 */
final class DuelController {

    /**
     * Khớp {@code dev.kage.character.domain.Character.NAME_MIN_LENGTH}/{@code NAME_MAX_LENGTH} phía
     * server, cùng cặp số {@code FriendController} đang dùng. Nhân đôi hằng số là CÓ CHỦ ĐÍCH:
     * client-core không được phụ thuộc module server, và luật thật vẫn ở server — con số ở đây chỉ
     * chặn một gói tin chắc chắn bị từ chối, không thay server phán quyết (Điều 24).
     */
    static final int MIN_NAME_LENGTH = 2;

    static final int MAX_NAME_LENGTH = 16;

    /**
     * Trần số cặp (id, tên) nhớ lại được. Sổ tên này lớn lên theo số người LẠ nói chuyện trong kênh
     * Thế giới — tức là theo hành vi của người khác, không phải của máy này — nên nó bắt buộc phải
     * có trần, cùng lập luận {@code ChatController.MAX_LINES}. Bỏ dòng CŨ NHẤT trước (LRU): người
     * vừa nói chuyện/vừa đứng cạnh mình mới là người sắp bị thách đấu.
     */
    static final int MAX_REMEMBERED_NAMES = 64;

    /**
     * Một nhân vật khác.
     *
     * @param name tên THẬT server gửi xuống — thứ DUY NHẤT gửi ngược lên được
     *     ({@code ChallengeDuelCommand} nhận {@code target_name}, không nhận id).
     * @param displayName bản đã qua {@link ChatController#sanitize}, có thể đã mất ký tự. Gửi bản
     *     này lên server là gửi một cái tên KHÁC — xem javadoc lớp.
     */
    record Player(long characterId, String name, String displayName) {}

    /** Điểm xếp hạng của CHÍNH mình, đúng như {@link GetPvpRatingResponse} gửi xuống. */
    record Rating(int rating, int wins, int losses) {}

    /** Lời thách đấu người khác gửi tới mình — {@code challengeId} là thứ duy nhất gửi ngược lên. */
    record Challenge(long challengeId, Player challenger) {}

    /**
     * Kết quả một trận đấu, đã quy chiếu về phía MÌNH.
     *
     * @param won mình thắng hay thua — suy ra bằng cách so id trong {@link DuelResultEvent} với id
     *     ĐỐI THỦ mà server đã báo ở {@link DuelStartedEvent}, không phải client tự phán.
     * @param newRating điểm xếp hạng mới của mình, do server tính (Elo) và gửi kèm.
     * @param opponentDisplayName tên đối thủ để hiện ra, đã sanitize.
     */
    record Result(boolean won, int newRating, String opponentDisplayName) {}

    /**
     * Kết quả biến chuỗi người chơi gõ thành tên đối thủ. Ba nhánh, {@code sealed} để chỗ gọi không
     * thể quên nhánh nào (/CLAUDE.md §7) — cùng hình dạng {@code FriendController.ComposeResult}.
     */
    sealed interface ComposeResult {

        /** Tên hợp lệ ở mức client, gửi đi được. Server vẫn là nơi quyết định cuối cùng. */
        record Send(String targetName) implements ComposeResult {}

        /** Không gửi, và nói cho người chơi biết vì sao. */
        record Reject(String messageKey) implements ComposeResult {}

        /** Ô nhập rỗng — không gửi, KHÔNG báo lỗi (bấm nhầm nút không đáng bị mắng). */
        enum Nothing implements ComposeResult {
            INSTANCE
        }
    }

    /**
     * Sổ tra id → tên THÔ. {@link LinkedHashMap} giữ đúng thứ tự nghe được cái tên lần gần nhất, để
     * {@link #rememberName} bỏ dòng cũ nhất khi chạm trần {@link #MAX_REMEMBERED_NAMES}.
     */
    private final Map<Long, String> knownNames = new LinkedHashMap<>();

    /** Người vừa được click trên màn hình, chờ bấm Thách đấu. {@code null} = chưa chọn ai. */
    private Player candidate;

    /** Lời thách đấu MỚI NHẤT đang chờ mình trả lời. {@code null} = không có. */
    private Challenge incoming;

    /** {@code challenge_id} của lời mình vừa gửi đi, {@code 0} = không có lời nào đang treo. */
    private long outgoingChallengeId;

    /** Đối thủ của trận đang diễn ra. {@code null} = không ở trong trận nào. */
    private Player opponent;

    /** Điểm xếp hạng theo lần hỏi gần nhất. {@code null} = chưa hỏi được lần nào. */
    private Rating rating;

    /**
     * Ghi nhớ một cặp (id, tên) vừa thấy trên dây — xem javadoc lớp về việc vì sao phải tự nhớ.
     *
     * <p>Bỏ qua id {@code 0} và tên rỗng: cả hai đều là giá trị mặc định của protobuf cho field
     * KHÔNG được set, tức "server không nói gì" chứ không phải "server nói rằng rỗng".
     */
    void rememberName(long characterId, String rawName) {
        if (characterId == 0L || rawName.isEmpty()) {
            return;
        }
        // Xoá rồi đặt lại: LinkedHashMap giữ thứ tự CHÈN, và một lần put đè lên khoá cũ KHÔNG đẩy
        // dòng đó về cuối hàng — thiếu dòng remove này thì người vừa nói chuyện lần thứ mười vẫn bị
        // coi là cũ nhất và bị bỏ trước.
        knownNames.remove(characterId);
        knownNames.put(characterId, rawName);
        if (knownNames.size() > MAX_REMEMBERED_NAMES) {
            Iterator<Map.Entry<Long, String>> oldestFirst =
                    knownNames.entrySet().iterator();
            oldestFirst.next();
            oldestFirst.remove();
        }
    }

    /** Số tên đang nhớ — chỉ để test kiểm trần LRU, không dùng cho hiển thị. */
    int rememberedNameCount() {
        return knownNames.size();
    }

    /**
     * Chọn một người trên màn hình làm đối thủ.
     *
     * @return người vừa chọn, hoặc rỗng khi client CHƯA BIẾT TÊN người này (khoảng trống #1 ở
     *     javadoc lớp). Chọn hụt thì GIỮ NGUYÊN người đã chọn trước đó — xoá mất lựa chọn cũ chỉ vì
     *     một cú click vào người lạ là làm hỏng thao tác đang dở của người chơi.
     */
    Optional<Player> selectCandidate(long characterId) {
        String name = knownNames.get(characterId);
        if (name == null) {
            return Optional.empty();
        }
        candidate = new Player(characterId, name, ChatController.sanitize(name));
        return Optional.of(candidate);
    }

    /** Người đang được chọn để thách đấu, rỗng khi chưa chọn ai. */
    Optional<Player> candidate() {
        return Optional.ofNullable(candidate);
    }

    /**
     * Lời thách đấu của mình đã tới server ({@code outcome = OK}).
     *
     * @param challengeId {@code challenge_id} server cấp — "chỉ có ý nghĩa khi outcome = OK"
     *     (arena.proto), nên chỗ gọi phải kiểm outcome TRƯỚC khi gọi hàm này.
     */
    void onChallengeSent(long challengeId) {
        outgoingChallengeId = challengeId;
    }

    /** Có lời thách đấu nào của mình đang chờ đối thủ trả lời không. */
    boolean hasOutgoingChallenge() {
        return outgoingChallengeId != 0L;
    }

    /**
     * Đối thủ vừa từ chối ({@code DuelChallengeDeclinedEvent}).
     *
     * @return {@code false} nếu id không khớp lời nào mình đang treo — gói lạc, không nói gì với
     *     người chơi thay vì báo một lời từ chối không có thật.
     */
    boolean onChallengeDeclined(long challengeId) {
        if (challengeId == 0L || challengeId != outgoingChallengeId) {
            return false;
        }
        outgoingChallengeId = 0L;
        return true;
    }

    /** Có người thách đấu mình — thay lời cũ (khoảng trống #2 ở javadoc lớp). */
    Player onChallengeReceived(DuelChallengeReceivedEvent event) {
        rememberName(event.getChallengerCharacterId(), event.getChallengerName());
        Player challenger = new Player(
                event.getChallengerCharacterId(),
                event.getChallengerName(),
                ChatController.sanitize(event.getChallengerName()));
        incoming = new Challenge(event.getChallengeId(), challenger);
        return challenger;
    }

    /** Lời thách đấu đang chờ mình trả lời, rỗng khi không có. */
    Optional<Challenge> incomingChallenge() {
        return Optional.ofNullable(incoming);
    }

    /**
     * Server đã trả lời cho lệnh Nhận/Từ chối của mình — dù {@code OK} hay {@code NOT_FOUND}, lời
     * thách đấu đó không còn là thứ đang chờ mình nữa (NOT_FOUND = nó đã biến mất từ trước).
     */
    void onChallengeAnswered() {
        incoming = null;
    }

    /**
     * Trận đấu bắt đầu thật (cả hai đã vào World riêng, ADR-026).
     *
     * <p>Xoá SẠCH cả hai lời thách đấu đang treo: lời mình gửi đã thành trận này, còn lời người khác
     * gửi tới thì server chắc chắn từ chối trong lúc mình đang bận
     * ({@code TARGET_ALREADY_IN_DUEL}) — bày một nút chắc chắn hỏng là nói dối người chơi.
     */
    Player onDuelStarted(DuelStartedEvent event) {
        rememberName(event.getOpponentCharacterId(), event.getOpponentName());
        opponent = new Player(
                event.getOpponentCharacterId(),
                event.getOpponentName(),
                ChatController.sanitize(event.getOpponentName()));
        incoming = null;
        outgoingChallengeId = 0L;
        return opponent;
    }

    /**
     * Trận đấu kết thúc (hạ gục hoặc bỏ cuộc).
     *
     * <p>Thắng hay thua suy ra bằng cách so id trong gói với id ĐỐI THỦ, không phải với id của mình:
     * lớp này không cần biết mình là ai, mà {@link DuelStartedEvent} thì luôn nói rõ đối thủ là ai.
     *
     * @return rỗng khi client không biết đối thủ là ai (VD vào lại giữa trận, {@code DuelStartedEvent}
     *     rơi vào phiên trước) — nói thẳng là không biết thay vì đoán bừa một bên thắng.
     */
    Optional<Result> onDuelResult(DuelResultEvent event) {
        Player finished = opponent;
        opponent = null;
        if (finished == null) {
            return Optional.empty();
        }
        if (event.getLoserCharacterId() == finished.characterId()) {
            return Optional.of(new Result(true, event.getWinnerNewRating(), finished.displayName()));
        }
        if (event.getWinnerCharacterId() == finished.characterId()) {
            return Optional.of(new Result(false, event.getLoserNewRating(), finished.displayName()));
        }
        return Optional.empty(); // gói của một trận khác — không phải trận mình vừa đánh.
    }

    /** Đang ở trong một trận đấu hay không, theo lời server (chỉ {@code DuelStartedEvent} bật được). */
    boolean inDuel() {
        return opponent != null;
    }

    /** Đối thủ của trận đang diễn ra, rỗng khi không ở trong trận nào. */
    Optional<Player> opponent() {
        return Optional.ofNullable(opponent);
    }

    void onRating(GetPvpRatingResponse response) {
        rating = new Rating(response.getRating(), response.getWins(), response.getLosses());
    }

    /** Điểm xếp hạng lần hỏi gần nhất, rỗng khi chưa hỏi được lần nào. */
    Optional<Rating> rating() {
        return Optional.ofNullable(rating);
    }

    /**
     * Biến chuỗi trong ô nhập thành tên đối thủ gửi đi được.
     *
     * @param raw đúng những gì đang nằm trong ô nhập
     * @return xem {@link ComposeResult}
     */
    ComposeResult composeTargetName(String raw) {
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return ComposeResult.Nothing.INSTANCE;
        }
        if (trimmed.length() < MIN_NAME_LENGTH || trimmed.length() > MAX_NAME_LENGTH) {
            return new ComposeResult.Reject("duel.error.bad_name");
        }
        return new ComposeResult.Send(trimmed);
    }

    /** Rời màn hình/mất kết nối — trạng thái đấu của một phiên chơi không sống sót sang phiên sau. */
    void clear() {
        knownNames.clear();
        candidate = null;
        incoming = null;
        outgoingChallengeId = 0L;
        opponent = null;
        rating = null;
    }

    // ---------------------------------------------------------------------------------------------
    // Bảng tra khoá i18n cho từng outcome.
    //
    // Bảng tra thay cho `switch` phủ hết enum — cùng cách GuildController làm, và cùng lý do: một
    // biểu thức switch phủ hết một enum protobuf tự nó đã vượt trần CyclomaticComplexity của PMD.
    //
    // UNSPECIFIED và UNRECOGNIZED CỐ TÌNH vắng mặt ở mọi bảng: cả hai rơi vào Optional.empty(), và
    // chỗ gọi dịch nó thành "duel.error.unknown". Cái sau là kết quả của một server MỚI HƠN client —
    // không thể liệt kê trước, chỉ có thể nói ra là không hiểu.
    // ---------------------------------------------------------------------------------------------

    private static final Map<ChallengeDuelOutcome, String> CHALLENGE_MESSAGE_KEYS = Map.of(
            ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_OK, "duel.notice.challenge_sent",
            ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_CANNOT_CHALLENGE_SELF, "duel.error.cannot_challenge_self",
            ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_ALREADY_IN_DUEL, "duel.error.already_in_duel",
            ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_TARGET_ALREADY_IN_DUEL, "duel.error.target_already_in_duel",
            ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_TARGET_NOT_FOUND, "duel.error.target_not_found",
            ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_TARGET_OFFLINE, "duel.error.target_offline");

    private static final Map<AcceptDuelOutcome, String> ACCEPT_MESSAGE_KEYS = Map.of(
            AcceptDuelOutcome.ACCEPT_DUEL_OUTCOME_OK, "duel.notice.accepted",
            AcceptDuelOutcome.ACCEPT_DUEL_OUTCOME_NOT_FOUND, "duel.error.challenge_not_found");

    private static final Map<DeclineDuelOutcome, String> DECLINE_MESSAGE_KEYS = Map.of(
            DeclineDuelOutcome.DECLINE_DUEL_OUTCOME_OK, "duel.notice.rejected",
            DeclineDuelOutcome.DECLINE_DUEL_OUTCOME_NOT_FOUND, "duel.error.challenge_not_found");

    private static final Map<ExitDuelOutcome, String> EXIT_MESSAGE_KEYS = Map.of(
            ExitDuelOutcome.EXIT_DUEL_OUTCOME_OK, "duel.notice.forfeited",
            ExitDuelOutcome.EXIT_DUEL_OUTCOME_NOT_IN_DUEL, "duel.error.not_in_duel");

    /**
     * Khoá i18n của một outcome, hoặc rỗng khi client không nhận ra giá trị đó.
     *
     * <p>Gộp về một hàm generic thay vì bốn hàm giống hệt nhau — cùng lý do
     * {@code GuildController.messageKey}: {@link Map#get} trả {@code null} cho khoá lạ, và
     * {@link Optional#ofNullable} là chỗ DUY NHẤT biến {@code null} đó thành thứ chỗ gọi không thể
     * quên xử lý (/CLAUDE.md §11: không trả {@code null}).
     */
    private static <T> Optional<String> messageKey(Map<T, String> table, T outcome) {
        return Optional.ofNullable(table.get(outcome));
    }

    static Optional<String> challengeMessageKey(ChallengeDuelOutcome outcome) {
        return messageKey(CHALLENGE_MESSAGE_KEYS, outcome);
    }

    static Optional<String> acceptMessageKey(AcceptDuelOutcome outcome) {
        return messageKey(ACCEPT_MESSAGE_KEYS, outcome);
    }

    static Optional<String> declineMessageKey(DeclineDuelOutcome outcome) {
        return messageKey(DECLINE_MESSAGE_KEYS, outcome);
    }

    static Optional<String> exitMessageKey(ExitDuelOutcome outcome) {
        return messageKey(EXIT_MESSAGE_KEYS, outcome);
    }
}
