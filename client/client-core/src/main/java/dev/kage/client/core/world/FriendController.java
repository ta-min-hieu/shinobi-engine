package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.kage.protocol.AcceptFriendRequestOutcome;
import dev.kage.protocol.CancelFriendRequestOutcome;
import dev.kage.protocol.FriendView;
import dev.kage.protocol.ListFriendsResponse;
import dev.kage.protocol.ListPendingFriendRequestsResponse;
import dev.kage.protocol.PendingFriendRequestView;
import dev.kage.protocol.RejectFriendRequestOutcome;
import dev.kage.protocol.RemoveFriendOutcome;
import dev.kage.protocol.SendFriendRequestOutcome;

/**
 * Trạng thái danh sách bạn bè (2026-08-25) — thuần Java, không libGDX/GL, test được thẳng (UI.md §4:
 * controller giữ trạng thái, widget chỉ vẽ). Cùng hình dạng {@link ChatController}.
 *
 * <p>Giữ hai danh sách server gửi xuống (bạn bè, lời mời đang chờ) và luật <b>biến một cái tên người
 * chơi gõ thành một lệnh gửi đi</b> ({@link #compose}).
 *
 * <p>Đây là màn hình THỨ HAI hiện chữ do <b>người khác</b> đặt ra (sau khung chat), nên luật sanitize
 * của {@link ChatController#sanitize} áp dụng nguyên vẹn: tên nhân vật là dữ liệu người khác chọn,
 * và font UI sinh từ {@code FontFactory.VIETNAMESE_CHARACTERS} <b>im lặng nuốt</b> mọi glyph ngoài
 * tập đó. Gọi thẳng hàm của {@code ChatController} thay vì nhân bản nó: cả hai ở cùng package và
 * luật này phải giống nhau tuyệt đối ở mọi chỗ hiện chữ của người lạ, hai bản sao là hai cơ hội để
 * chúng lệch nhau.
 *
 * <p>Trạng thái online <b>không</b> nằm trong DB (friend.proto) — server ghép lúc trả
 * {@link ListFriendsResponse} rồi sau đó chỉ đẩy delta qua {@code FriendOnlineEvent}/
 * {@code FriendOfflineEvent}. Vì vậy lớp này phải tự vá cột online tại chỗ ({@link #onPresenceChanged})
 * chứ không thể hỏi lại server mỗi lần có người vào/ra.
 */
final class FriendController {

    /**
     * Khớp {@code dev.kage.character.domain.Character.NAME_MIN_LENGTH}/{@code NAME_MAX_LENGTH} phía
     * server. Nhân đôi hằng số là CÓ CHỦ ĐÍCH, cùng lý do {@link ChatController#MAX_BODY_LENGTH}:
     * client-core không được phụ thuộc module server, và luật thật vẫn ở server — con số ở đây chỉ
     * để chặn một gói tin chắc chắn bị từ chối, không phải để thay server phán quyết.
     */
    static final int MIN_NAME_LENGTH = 2;

    static final int MAX_NAME_LENGTH = 16;

    /**
     * Một người bạn. {@code online} do gateway ghép, KHÔNG phải dữ liệu bền — xem javadoc lớp.
     *
     * <p>Giữ HAI cái tên và đó không phải thừa: {@code name} là tên THẬT server gửi xuống, thứ duy
     * nhất gửi ngược lên được ({@code RemoveFriendCommand} nhận {@code target_name}, không nhận id);
     * {@code displayName} là bản đã qua {@code ChatController.sanitize}, có thể đã mất ký tự. Gửi
     * bản hiển thị lên server là gửi một cái tên KHÁC — nghĩa là không bao giờ xoá được một người
     * bạn có tên chứa ký tự font UI không vẽ nổi.
     */
    record Friend(long characterId, String name, String displayName, boolean online, int intimacy) {}

    /**
     * Một lời mời NGƯỜI KHÁC gửi tới mình. {@code friendshipId} là thứ duy nhất gửi ngược lên được
     * khi chấp nhận/từ chối (friend.proto) — tên chỉ để hiện ra cho người chơi đọc.
     */
    record PendingRequest(long friendshipId, String requesterName, String message) {}

    /** Kết quả biến ô nhập tên thành hành động. Ba nhánh, {@code sealed} như {@link ChatController}. */
    sealed interface ComposeResult {

        /** Gửi thật — server tự tra tên và trả {@code TARGET_NOT_FOUND} nếu sai (Điều 24). */
        record Send(String targetName) implements ComposeResult {}

        /** Không gửi, và nói cho người chơi biết vì sao. */
        record Reject(String messageKey) implements ComposeResult {}

        /** Ô nhập rỗng — không gửi, KHÔNG báo lỗi (bấm Enter nhầm không đáng bị mắng). */
        enum Nothing implements ComposeResult {
            INSTANCE
        }
    }

    private final List<Friend> friends = new ArrayList<>();
    private final java.util.Map<Long, Long> lastOnline = new java.util.HashMap<>();

    private final List<PendingRequest> pendingRequests = new ArrayList<>();

    /**
     * Biến tên vừa gõ thành lệnh gửi lời mời.
     *
     * <p>Chỉ kiểm ĐỘ DÀI, không kiểm bộ ký tự dù server có thêm luật {@code [\p{L}\p{N}_]+}: gõ sai
     * charset thì server trả {@code TARGET_NOT_FOUND} — đúng câu trả lời, vì một cái tên không hợp
     * lệ chắc chắn không thuộc về ai. Chép luật charset sang đây chỉ tạo thêm một chỗ để lệch với
     * server khi luật kia đổi.
     *
     * @param raw đúng những gì đang nằm trong ô nhập
     * @return xem {@link ComposeResult}
     */
    ComposeResult compose(String raw) {
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return ComposeResult.Nothing.INSTANCE;
        }
        if (trimmed.length() < MIN_NAME_LENGTH || trimmed.length() > MAX_NAME_LENGTH) {
            return new ComposeResult.Reject("friend.error.bad_name");
        }
        return new ComposeResult.Send(trimmed);
    }

    /**
     * Toàn bộ danh sách bạn bè mới nhất — server luôn gửi TRỌN danh sách, không gửi delta, nên chỗ
     * này thay thế chứ không hợp nhất.
     *
     * <p>Sắp xếp NGAY LÚC NHẬN chứ không mỗi lần vẽ: người đang online lên trước để danh sách dài
     * vẫn có ích. Cố tình KHÔNG sắp lại sau mỗi {@link #onPresenceChanged} — một người bạn vừa
     * online mà cả danh sách nhảy chỗ ngay dưới con trỏ chuột là cách chắc chắn để bấm nhầm nút Xoá.
     */
    void onFriendList(ListFriendsResponse response) {
        friends.clear();
        lastOnline.clear();
        for (FriendView view : response.getFriendsList()) {
            lastOnline.put(view.getCharacterId(), view.getLastOnlineEpochSeconds()); // ADR-192
            friends.add(new Friend(
                    view.getCharacterId(), view.getName(), displayNameOf(view), view.getOnline(), view.getIntimacy()));
        }
        friends.sort(Comparator.comparing(Friend::online).reversed().thenComparing(Friend::displayName));
    }

    /** Tên hiển thị: tên, kèm ghi chú riêng trong ngoặc (ADR-173) và lời nhắn trạng thái của bạn (ADR-335). */
    static String displayNameOf(FriendView view) {
        String name = ChatController.sanitize(view.getName());
        String note = ChatController.sanitize(view.getNote());
        String status = ChatController.sanitize(view.getStatusMessage());
        String shown = note.isBlank() ? name : name + " (" + note + ")";
        return status.isBlank() ? shown : shown + " — " + status;
    }

    /** Toàn bộ lời mời đang chờ — cùng quy ước "thay thế trọn gói" như {@link #onFriendList}. */
    void onPendingRequests(ListPendingFriendRequestsResponse response) {
        pendingRequests.clear();
        for (PendingFriendRequestView view : response.getRequestsList()) {
            pendingRequests.add(new PendingRequest(
                    view.getFriendshipId(),
                    ChatController.sanitize(view.getRequesterName()),
                    ChatController.sanitize(view.getMessage()))); // ADR-376
        }
    }

    /**
     * Một người bạn vừa vào/ra thế giới.
     *
     * <p>Trả về TÊN chứ không phải {@code boolean} vì hai gói này chỉ mang {@code character_id}
     * (friend.proto) — muốn nói "X vừa trực tuyến" thì phải tự tra tên trong danh sách đang giữ.
     *
     * @param characterId người vừa đổi trạng thái
     * @param online {@code true} nếu vừa vào thế giới
     * @return tên HIỂN THỊ của người đó, hoặc {@link Optional#empty()} nếu KHÔNG có trong danh sách đang giữ —
     *     nghĩa là danh sách đã cũ (VD ai đó vừa chấp nhận lời mời của mình trong lúc mình đang chơi:
     *     giao thức không có gói nào báo "hai người vừa thành bạn"). Chỗ gọi phải hỏi lại danh sách.
     */
    Optional<String> onPresenceChanged(long characterId, boolean online) {
        for (int index = 0; index < friends.size(); index++) {
            Friend friend = friends.get(index);
            if (friend.characterId() == characterId) {
                lastOnline.put(
                        characterId, online ? 0L : java.time.Instant.now().getEpochSecond());
                friends.set(
                        index, new Friend(characterId, friend.name(), friend.displayName(), online, friend.intimacy()));
                return Optional.of(friend.displayName());
            }
        }
        return Optional.empty();
    }

    /** ADR-192 — giây epoch lần cuối online của một người bạn đang offline; 0 = không biết. */
    long lastOnlineOf(long characterId) {
        return lastOnline.getOrDefault(characterId, 0L);
    }

    /** ADR-364 — người tên {@code name} có trong danh sách bạn đang giữ không. */
    boolean hasFriendNamed(String name) {
        for (Friend friend : friends) {
            if (friend.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** Danh sách bạn bè, online trước — xem {@link #onFriendList} về thứ tự. */
    List<Friend> friends() {
        return List.copyOf(friends);
    }

    /** Lời mời đang chờ, đúng thứ tự server gửi. */
    List<PendingRequest> pendingRequests() {
        return List.copyOf(pendingRequests);
    }

    /** Rời màn hình/mất kết nối — danh sách của một phiên chơi không được sống sót sang phiên sau. */
    void clear() {
        friends.clear();
        pendingRequests.clear();
    }

    /**
     * Khoá i18n cho từng kết quả gửi lời mời. Bảng tra thay cho {@code switch} phủ hết enum —
     * cùng cách {@code ChatController.OUTCOME_MESSAGE_KEYS} làm, và cùng lý do: một biểu thức switch
     * phủ hết một enum protobuf tự nó đã vượt trần {@code CyclomaticComplexity} của PMD.
     *
     * <p>Khác {@code ChatController}, {@code OK} ở đây CŨNG có câu trả lời: gửi lời mời thành công
     * không đổi gì trên màn hình của chính người gửi (danh sách bạn chưa có thêm ai, lời mời nằm ở
     * phía người nhận) — im lặng thì không phân biệt được với một cú bấm rơi vào hư không.
     */
    private static final Map<SendFriendRequestOutcome, String> SEND_OUTCOME_MESSAGE_KEYS = Map.of(
            SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_OK, "friend.notice.request_sent",
            SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_TARGET_NOT_FOUND, "friend.error.target_not_found",
            SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_CANNOT_FRIEND_SELF, "friend.error.cannot_friend_self",
            SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_ALREADY_FRIENDS, "friend.error.already_friends",
            SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_REQUEST_ALREADY_PENDING,
                    "friend.error.already_pending");

    private static final Map<AcceptFriendRequestOutcome, String> ACCEPT_OUTCOME_MESSAGE_KEYS = Map.of(
            AcceptFriendRequestOutcome.ACCEPT_FRIEND_REQUEST_OUTCOME_OK, "friend.notice.accepted",
            AcceptFriendRequestOutcome.ACCEPT_FRIEND_REQUEST_OUTCOME_NOT_FOUND, "friend.error.request_not_found");

    private static final Map<RejectFriendRequestOutcome, String> REJECT_OUTCOME_MESSAGE_KEYS = Map.of(
            RejectFriendRequestOutcome.REJECT_FRIEND_REQUEST_OUTCOME_OK, "friend.notice.rejected",
            RejectFriendRequestOutcome.REJECT_FRIEND_REQUEST_OUTCOME_NOT_FOUND, "friend.error.request_not_found");

    private static final Map<RemoveFriendOutcome, String> REMOVE_OUTCOME_MESSAGE_KEYS = Map.of(
            RemoveFriendOutcome.REMOVE_FRIEND_OUTCOME_OK, "friend.notice.removed",
            RemoveFriendOutcome.REMOVE_FRIEND_OUTCOME_TARGET_NOT_FOUND, "friend.error.target_not_found",
            RemoveFriendOutcome.REMOVE_FRIEND_OUTCOME_NOT_FRIENDS, "friend.error.not_friends");

    private static final Map<CancelFriendRequestOutcome, String> CANCEL_OUTCOME_MESSAGE_KEYS = Map.of(
            CancelFriendRequestOutcome.CANCEL_FRIEND_REQUEST_OUTCOME_OK, "friend.notice.withdrawn",
            CancelFriendRequestOutcome.CANCEL_FRIEND_REQUEST_OUTCOME_TARGET_NOT_FOUND, "friend.error.target_not_found",
            CancelFriendRequestOutcome.CANCEL_FRIEND_REQUEST_OUTCOME_NOT_PENDING, "friend.error.no_pending_request");

    /**
     * Câu giải thích một kết quả. Bốn hàm chứ không phải một hàm tổng quát: bốn enum protobuf khác
     * nhau không có tổ tiên chung nào ngoài {@code Object}, gộp lại chỉ mua được một chữ ký nhận
     * {@code Object} — thứ vứt đi đúng cái mà trình biên dịch đang giúp mình.
     *
     * <p>{@code UNSPECIFIED}/{@code UNRECOGNIZED} CỐ TÌNH vắng mặt trong mọi bảng: cái sau là kết
     * quả của một server mới hơn client, không liệt kê trước được, chỉ nói ra là không hiểu.
     */
    static String sendOutcomeMessageKey(SendFriendRequestOutcome outcome) {
        return SEND_OUTCOME_MESSAGE_KEYS.getOrDefault(outcome, "friend.error.unknown");
    }

    /** Xem {@link #sendOutcomeMessageKey}. */
    static String acceptOutcomeMessageKey(AcceptFriendRequestOutcome outcome) {
        return ACCEPT_OUTCOME_MESSAGE_KEYS.getOrDefault(outcome, "friend.error.unknown");
    }

    /** Xem {@link #sendOutcomeMessageKey}. */
    static String rejectOutcomeMessageKey(RejectFriendRequestOutcome outcome) {
        return REJECT_OUTCOME_MESSAGE_KEYS.getOrDefault(outcome, "friend.error.unknown");
    }

    /** Xem {@link #sendOutcomeMessageKey}. */
    static String removeOutcomeMessageKey(RemoveFriendOutcome outcome) {
        return REMOVE_OUTCOME_MESSAGE_KEYS.getOrDefault(outcome, "friend.error.unknown");
    }

    /** ADR-074 — xem {@link #sendOutcomeMessageKey}. */
    static String cancelOutcomeMessageKey(CancelFriendRequestOutcome outcome) {
        return CANCEL_OUTCOME_MESSAGE_KEYS.getOrDefault(outcome, "friend.error.unknown");
    }
}
