package dev.kage.client.core.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.kage.client.core.render.FontFactory;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.SendChatMessageOutcome;

/**
 * Trạng thái khung chat (2026-08-25) — thuần Java, không libGDX/GL, test được thẳng (UI.md §4:
 * controller giữ trạng thái, widget chỉ vẽ). Cùng hình dạng {@link DialogueController}.
 *
 * <p>Giữ ba thứ: <b>nhật ký</b> vài dòng gần nhất, <b>kênh đang chọn</b>, và luật <b>biến một dòng
 * người chơi gõ thành một lệnh gửi đi</b> ({@link #compose}).
 *
 * <p>Chat là chỗ ĐẦU TIÊN trong dự án màn hình hiện chữ do <b>người khác</b> gõ, nên hai luật dưới
 * đây không có tiền lệ ở HUD/hội thoại (mọi chữ ở đó đều từ bundle i18n của chính dự án):
 *
 * <ul>
 *   <li>Chữ ngoài tập glyph của font UI bị font <b>im lặng nuốt mất</b> — xem
 *       {@link FontFactory#canRender(char)}. {@link #sanitize} đổi chúng thành {@code ?} để tin nhắn
 *       cụt trông ra cụt, thay vì trông như người kia gõ thiếu.
 *   <li>Nhật ký có TRẦN cứng {@link #MAX_LINES}: đây là dữ liệu do người khác bơm vào với nhịp do họ
 *       quyết, không phải do máy này quyết — giữ hết là để người lạ quyết bộ nhớ của mình.
 * </ul>
 */
final class ChatController {

    /**
     * Khớp {@code dev.kage.chat.domain.ChatMessage.MAX_BODY_LENGTH} phía server. Nhân đôi hằng số
     * là CÓ CHỦ ĐÍCH: client-core không được phụ thuộc module server (ARCHITECTURE.md), và luật
     * thật vẫn nằm ở server — con số ở đây chỉ để báo trước cho người chơi thay vì để họ gõ xong
     * mới biết bị từ chối. Server lệch xuống thấp hơn thì client vẫn an toàn (chỉ hiện lỗi muộn).
     */
    static final int MAX_BODY_LENGTH = 200;

    /**
     * Số dòng nhật ký giữ lại — xem javadoc lớp về lý do phải có trần.
     *
     * <p>6 chứ không phải 8: một dòng dài xuống hàng thành HAI dòng hình học, nên 8 dòng có thể ngốn
     * tới ~11 hàng chữ và đẩy khung chat cao gần nửa màn hình ở 720p (thấy thật trên ảnh chụp
     * {@code runHudPreview} đầu tiên). Khung neo góc trái-dưới và nở LÊN TRÊN, nên chiều cao của nó
     * là thứ ăn vào tầm nhìn thế giới chứ không phải vào HUD.
     *
     * <p>ADR-363 (2026-11-14): nhật ký giữ tới 12 dòng; khung vẫn HIỆN 6 theo mặc định
     * ({@code ChatPanel.DEFAULT_VISIBLE_LINES}), người chơi tự chọn 4–12 bằng {@code /dongchat}.
     */
    static final int MAX_LINES = 40; // ADR-395 — giữ 40 dòng để cuộn; hiện tối đa ChatPanel.MAX_VISIBLE_LINES

    /** Tiền tố tin nhắn riêng: {@code /w <tên> <nội dung>}. */
    private static final String PRIVATE_PREFIX = "/w ";

    /** ADR-222 — tiền tố một tin lên kênh giao dịch: {@code /gd <nội dung>} (hoặc {@code /trade}). */
    private static final String TRADE_PREFIX_VI = "/gd ";

    private static final String TRADE_PREFIX_EN = "/trade ";

    /** ADR-222/229 — kênh người chơi đã tắt hiển thị (tin tới vẫn nhận, chỉ không thêm vào khung chat). */
    private final java.util.Set<ChatChannel> hiddenChannels = java.util.EnumSet.noneOf(ChatChannel.class);

    /**
     * Kênh mặc định lúc vào game. Gửi một tin riêng bằng {@code /w} KHÔNG đổi giá trị này — nhắn
     * riêng một câu rồi bị kẹt ở kênh riêng là cách quen thuộc nhất để lỡ tay nói to chuyện riêng
     * (hoặc ngược lại), nên kênh chỉ đổi khi người chơi CHỦ ĐỘNG bấm nút.
     */
    private static final ChatChannel DEFAULT_CHANNEL = ChatChannel.CHAT_CHANNEL_WORLD;

    private final Deque<Line> lines = new ArrayDeque<>();

    private ChatChannel channel = DEFAULT_CHANNEL;

    /**
     * Có đang ở trong một nhóm hay không — quyết định kênh Nhóm có nằm trong vòng xoay của
     * {@link #toggleChannel()} hay không. Do {@code PartyCoordinator} cập nhật.
     */
    private boolean partyAvailable;

    /**
     * Một dòng trong nhật ký. {@code senderName} rỗng = dòng của hệ thống (báo lỗi gửi), không phải
     * của người chơi nào — widget dựa vào đó để không vẽ dấu hai chấm thừa.
     */
    record Line(ChatChannel channel, String senderName, String body, long receivedAtMillis) {

        /** Dòng nhận ngay lúc này (ADR-268 — giờ trước tin chat đọc {@link #receivedAtMillis}). */
        Line(ChatChannel channel, String senderName, String body) {
            this(channel, senderName, body, System.currentTimeMillis());
        }
    }

    /**
     * Kết quả biến một dòng gõ vào thành hành động. Ba nhánh, {@code sealed} để chỗ gọi không thể
     * quên nhánh nào (/CLAUDE.md §7).
     */
    sealed interface ComposeResult {

        /** Gửi thật. {@code targetName} chỉ có nghĩa với {@link ChatChannel#CHAT_CHANNEL_PRIVATE}. */
        record Send(ChatChannel channel, String targetName, String body) implements ComposeResult {}

        /** Không gửi, và nói cho người chơi biết vì sao. */
        record Reject(String messageKey) implements ComposeResult {}

        /** Ô nhập rỗng — đóng ô, KHÔNG báo lỗi (bấm Enter nhầm không đáng bị mắng). */
        enum Nothing implements ComposeResult {
            INSTANCE
        }
    }

    /**
     * Biến một dòng người chơi vừa gõ thành lệnh gửi.
     *
     * <p>Cú pháp {@code /w <tên> <nội dung>} là lối vào DUY NHẤT của kênh riêng ở lát cắt này: chọn
     * người nhận đúng cách cần danh sách bạn bè/party trên màn hình, mà client chưa nối opcode nào
     * của hai thứ đó (1100-1199 và 1300-1399 còn trống trơn phía client). Gõ tên tay thì làm được
     * ngay và server đã tự tra tên rồi trả {@code TARGET_NOT_FOUND}/{@code TARGET_OFFLINE} nếu sai,
     * nên không có gì để client đoán cả.
     *
     * @param raw đúng những gì đang nằm trong ô nhập
     * @return xem {@link ComposeResult}
     */
    ComposeResult compose(String raw) {
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            return ComposeResult.Nothing.INSTANCE;
        }
        if (trimmed.startsWith(PRIVATE_PREFIX)) {
            return composePrivate(trimmed.substring(PRIVATE_PREFIX.length()).strip());
        }
        for (String prefix : new String[] {TRADE_PREFIX_VI, TRADE_PREFIX_EN}) {
            if (trimmed.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                return validated(
                        ChatChannel.CHAT_CHANNEL_TRADE,
                        "",
                        trimmed.substring(prefix.length()).strip());
            }
        }
        return validated(channel, "", trimmed);
    }

    private ComposeResult composePrivate(String afterPrefix) {
        int separator = afterPrefix.indexOf(' ');
        if (separator < 0) {
            // Có tên nhưng chưa có nội dung — gửi đi chỉ tổ ăn một lượt rate limit vô nghĩa
            // (GatewayChannelHandler ngắt kết nối khi vượt liên tục).
            return new ComposeResult.Reject("chat.error.private_needs_body");
        }
        String targetName = afterPrefix.substring(0, separator);
        String body = afterPrefix.substring(separator + 1).strip();
        if (targetName.isEmpty() || body.isEmpty()) {
            return new ComposeResult.Reject("chat.error.private_needs_body");
        }
        return validated(ChatChannel.CHAT_CHANNEL_PRIVATE, targetName, body);
    }

    private ComposeResult validated(ChatChannel target, String targetName, String body) {
        if (body.length() > MAX_BODY_LENGTH) {
            return new ComposeResult.Reject("chat.error.too_long");
        }
        return new ComposeResult.Send(target, targetName, body);
    }

    /**
     * Người chơi vừa vào/rời một nhóm — kênh Nhóm xuất hiện/biến mất khỏi vòng xoay theo đó.
     *
     * <p>Không bày kênh Nhóm ra khi chưa vào nhóm nào: một nút chỉ để nhận {@code NOT_IN_PARTY} là
     * một nút hỏng. Và nếu đang CHỌN kênh Nhóm mà vừa rời nhóm thì phải rơi về Thế giới ngay — bằng
     * không câu tiếp theo gõ vào sẽ bay đi rồi bật lại lỗi, trong khi người chơi tưởng mình đang nói
     * với ai đó.
     */
    void setPartyAvailable(boolean available) {
        partyAvailable = available;
        if (!available && channel == ChatChannel.CHAT_CHANNEL_PARTY) {
            channel = DEFAULT_CHANNEL;
        }
    }

    /**
     * Đổi kênh gửi mặc định: Thế giới → Giao dịch (ADR-222) → Khu vực → (Nhóm nếu đang ở trong nhóm) → Thế giới.
     *
     * <p>Bang hội vẫn KHÔNG có trong vòng xoay: client chưa nối một opcode nào của Guild (700-799)
     * nên không vào bang được để mà nhắn. Kênh riêng đi qua {@code /w} ({@link #compose}) vì nó cần
     * thêm một tham số mà nút xoay vòng không mang được.
     */
    void toggleChannel() {
        channel = switch (channel) {
            case CHAT_CHANNEL_WORLD -> ChatChannel.CHAT_CHANNEL_TRADE; // ADR-222
            case CHAT_CHANNEL_TRADE -> ChatChannel.CHAT_CHANNEL_AREA;
            case CHAT_CHANNEL_AREA -> partyAvailable ? ChatChannel.CHAT_CHANNEL_PARTY : DEFAULT_CHANNEL;
            default -> DEFAULT_CHANNEL;
        };
    }

    ChatChannel channel() {
        return channel;
    }

    /**
     * ADR-371 — chọn lại kênh đã nhớ; chỉ nhận kênh có trong vòng xoay (Nhóm chỉ khi đang ở trong nhóm), kênh khác bị
     * bỏ qua. Trả {@code true} nếu đã đổi.
     */
    boolean selectChannel(ChatChannel wanted) {
        boolean allowed = wanted == ChatChannel.CHAT_CHANNEL_WORLD
                || wanted == ChatChannel.CHAT_CHANNEL_TRADE
                || wanted == ChatChannel.CHAT_CHANNEL_AREA
                || (wanted == ChatChannel.CHAT_CHANNEL_PARTY && partyAvailable);
        if (allowed) {
            channel = wanted;
        }
        return allowed;
    }

    /** Tin nhắn từ server — của người khác, hoặc của chính mình dội lại (chat.proto). */
    void onMessage(ChatMessageEvent event) {
        if (hiddenChannels.contains(event.getChannel())) {
            return; // ADR-222/229 — kênh đã tắt hiển thị
        }
        append(new Line(event.getChannel(), sanitize(event.getSenderName()), sanitize(event.getBody())));
    }

    /** ADR-222/229 — bật / tắt hiển thị một kênh (dòng hệ thống không bao giờ bị ẩn). */
    void setChannelVisible(ChatChannel channel, boolean visible) {
        if (visible) {
            hiddenChannels.remove(channel);
        } else {
            hiddenChannels.add(channel);
        }
    }

    boolean isChannelVisible(ChatChannel channel) {
        return !hiddenChannels.contains(channel);
    }

    /**
     * Ghi một dòng của HỆ THỐNG vào chính nhật ký chat thay vì bắn chữ nổi giữa màn hình như
     * {@code CombatFeedbackPresenter}: lý do gửi hỏng ("nhắn quá nhanh", "người này offline") chỉ có
     * nghĩa khi đọc CẠNH tin nhắn vừa gõ, và người chơi lúc đó đang nhìn vào khung chat chứ không
     * nhìn vào nhân vật.
     *
     * @param text chữ đã dịch sẵn (khoá i18n tra ở lớp widget, chỗ có bundle)
     */
    void appendSystem(String text) {
        append(new Line(ChatChannel.CHAT_CHANNEL_UNSPECIFIED, "", sanitize(text)));
    }

    private void append(Line line) {
        if (lines.size() == MAX_LINES) {
            lines.removeFirst();
        }
        lines.addLast(line);
    }

    /** Nhật ký, CŨ trước MỚI sau — đúng thứ tự vẽ từ trên xuống. */
    List<Line> lines() {
        return List.copyOf(lines);
    }

    /**
     * Thay nhật ký bằng {@code restored} (cũ trước mới sau) — vẫn giữ trần {@link #MAX_LINES}: chỉ giữ
     * những dòng MỚI nhất nếu bản mang sang dài hơn.
     */
    void restoreLines(List<Line> restored) {
        lines.clear();
        for (Line line : restored) {
            append(line);
        }
    }

    void clear() {
        lines.clear();
        channel = DEFAULT_CHANNEL;
        partyAvailable = false;
    }

    /**
     * Khoá i18n cho từng kết quả gửi KHÁC {@code OK}. Bảng tra thay cho {@code switch} 10 nhánh —
     * cùng cách {@code GatewayChannelHandler} làm với các bảng {@code *_OUTCOME_MAPPING} của nó, và
     * cùng lý do: một biểu thức switch phủ hết một enum protobuf tự nó đã vượt trần
     * {@code CyclomaticComplexity} của PMD.
     *
     * <p>{@code UNSPECIFIED} và {@code UNRECOGNIZED} CỐ TÌNH vắng mặt: cả hai rơi vào nhánh mặc định
     * của {@link #outcomeMessageKey}. Cái sau là kết quả của một server mới hơn client — không thể
     * liệt kê trước, chỉ có thể nói ra là không hiểu.
     */
    private static final Map<SendChatMessageOutcome, String> OUTCOME_MESSAGE_KEYS = Map.ofEntries(
            Map.entry(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_MESSAGE_BLANK, "chat.error.blank"),
            Map.entry(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_MESSAGE_TOO_LONG, "chat.error.too_long"),
            Map.entry(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_CONTAINS_BANNED_WORD, "chat.error.banned_word"),
            Map.entry(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_TARGET_NOT_FOUND, "chat.error.target_not_found"),
            Map.entry(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_TARGET_OFFLINE, "chat.error.target_offline"),
            Map.entry(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_RATE_LIMITED, "chat.error.rate_limited"),
            Map.entry(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_NOT_IN_GUILD, "chat.error.not_in_guild"),
            Map.entry(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_NOT_IN_PARTY, "chat.error.not_in_party"),
            Map.entry(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_MUTED, "chat.error.muted"),
            Map.entry(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_NOT_SWORN, "chat.error.not_sworn"),
            Map.entry(SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_DELIVERED_LATER, "chat.notice.delivered_later"),
            Map.entry(
                    SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_RECIPIENT_INBOX_FULL,
                    "chat.error.recipient_inbox_full"),
            Map.entry( // ADR-188
                    SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_LINKED_ITEM_NOT_FOUND,
                    "chat.error.linked_item_not_found"),
            Map.entry(
                    SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_LINKED_ITEM_NOT_ON_AREA,
                    "chat.error.linked_item_not_on_area"),
            Map.entry( // ADR-368
                    SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_LEVEL_TOO_LOW, "chat.error.level_too_low"),
            Map.entry( // ADR-369
                    SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_STRANGER_LIMIT, "chat.error.stranger_limit"));

    /**
     * Câu giải thích một kết quả gửi, hoặc rỗng khi không có gì để nói.
     *
     * <p>Rỗng CHỈ với {@code OK}: tin nhắn gửi xong tự hiện ra qua {@code ChatMessageEvent}, thêm
     * một dòng "đã gửi" nữa là nói hai lần cùng một chuyện.
     *
     * <p>{@code PRIVATE} là kênh DUY NHẤT server không dội tin về cho chính người gửi (chat.proto:
     * chỉ 1 người nhận) — xem {@link ChatCoordinator} về việc cố tình không bù dòng đó.
     *
     * @param outcome kết quả server trả về
     * @return khoá i18n, hoặc {@link Optional#empty()} nếu không cần nói gì
     */
    static Optional<String> outcomeMessageKey(SendChatMessageOutcome outcome) {
        if (outcome == SendChatMessageOutcome.SEND_CHAT_MESSAGE_OUTCOME_OK) {
            return Optional.empty();
        }
        return Optional.of(OUTCOME_MESSAGE_KEYS.getOrDefault(outcome, "chat.error.unknown"));
    }

    /**
     * Đổi mọi ký tự font UI không vẽ được thành {@code ?} — xem javadoc lớp và
     * {@link FontFactory#canRender(char)}.
     *
     * <p>Trả về CHÍNH chuỗi vào nếu không có gì phải đổi (trường hợp gần như luôn xảy ra): tin nhắn
     * tới theo nhịp người khác gõ chứ không phải mỗi khung hình, nhưng cấp phát một chuỗi mới cho
     * mỗi dòng tiếng Việt hợp lệ vẫn là cấp phát không đổi lấy gì.
     */
    static String sanitize(String raw) {
        for (int index = 0; index < raw.length(); index++) {
            if (!FontFactory.canRender(raw.charAt(index))) {
                return replaceUnrenderable(raw, index);
            }
        }
        return raw;
    }

    private static String replaceUnrenderable(String raw, int firstBadIndex) {
        StringBuilder builder = new StringBuilder(raw);
        for (int index = firstBadIndex; index < builder.length(); index++) {
            if (!FontFactory.canRender(builder.charAt(index))) {
                builder.setCharAt(index, '?');
            }
        }
        return builder.toString();
    }
}
